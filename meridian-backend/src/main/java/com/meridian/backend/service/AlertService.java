package com.meridian.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.backend.dto.AlertRequest;
import com.meridian.backend.dto.AlertResponse;
import com.meridian.backend.dto.AlertTriggeredMessage;
import com.meridian.backend.exception.AlertNotFoundException;
import com.meridian.backend.exception.TickerNotFoundException;
import com.meridian.backend.model.Alert;
import com.meridian.backend.model.AlertDirection;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.AlertRepository;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.websocket.PriceWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final TickerRepository tickerRepository;
    private final PriceWebSocketHandler priceWebSocketHandler;
    private final ObjectMapper objectMapper;

    public AlertService(AlertRepository alertRepository,
                         TickerRepository tickerRepository,
                         PriceWebSocketHandler priceWebSocketHandler,
                         ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.tickerRepository = tickerRepository;
        this.priceWebSocketHandler = priceWebSocketHandler;
        this.objectMapper = objectMapper;
    }

    public AlertResponse createAlert(AlertRequest request, User user) {
        Ticker ticker = tickerRepository.findBySymbol(request.symbol())
                .orElseThrow(() -> new TickerNotFoundException(request.symbol()));

        Alert alert = alertRepository.save(new Alert(user, ticker, request.direction(), request.targetPrice()));
        return toResponse(alert);
    }

    public List<AlertResponse> getAlerts(User user) {
        return alertRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public void deleteAlert(Long id, User user) {
        Alert alert = alertRepository.findById(id)
                .filter(a -> a.getUser().getId().equals(user.getId())) // only the owner can delete it
                .orElseThrow(() -> new AlertNotFoundException(id));
        alertRepository.delete(alert);
    }

    // Called every time a new price is saved. Checks every untriggered alert
    // for that specific ticker (across ALL users, since alerts are personal
    // but prices are shared) and fires any that just crossed their target.
    public void checkAlertsForTicker(Ticker ticker, BigDecimal currentPrice) {
        List<Alert> candidates = alertRepository.findByTickerIdAndTriggeredFalse(ticker.getId());

        for (Alert alert : candidates) {
            boolean shouldTrigger = alert.getDirection() == AlertDirection.ABOVE
                    ? currentPrice.compareTo(alert.getTargetPrice()) >= 0
                    : currentPrice.compareTo(alert.getTargetPrice()) <= 0;

            if (shouldTrigger) {
                Instant triggeredAt = Instant.now();
                alert.setTriggered(true);
                alert.setTriggeredAt(triggeredAt);
                alertRepository.save(alert);

                broadcastTrigger(alert, currentPrice, triggeredAt);
            }
        }
    }

    private void broadcastTrigger(Alert alert, BigDecimal currentPrice, Instant triggeredAt) {
        try {
            AlertTriggeredMessage message = new AlertTriggeredMessage(
                    "ALERT_TRIGGERED", alert.getId(), alert.getTicker().getSymbol(),
                    alert.getDirection(), alert.getTargetPrice(), currentPrice, triggeredAt
            );
            String json = objectMapper.writeValueAsString(message);
            priceWebSocketHandler.broadcastToUser(alert.getUser().getId(), json);
        } catch (Exception e) {
            log.warn("Failed to broadcast alert trigger for alert {}", alert.getId(), e);
        }
    }

    private AlertResponse toResponse(Alert alert) {
        return new AlertResponse(
                alert.getId(), alert.getTicker().getSymbol(), alert.getDirection(),
                alert.getTargetPrice(), alert.isTriggered(), alert.getCreatedAt(), alert.getTriggeredAt()
        );
    }
}
