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
import com.meridian.backend.mail.EmailNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
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
    private final EmailNotifier emailNotifier;
    private final String publicUrl;

    public AlertService(AlertRepository alertRepository,
                         TickerRepository tickerRepository,
                         PriceWebSocketHandler priceWebSocketHandler,
                         ObjectMapper objectMapper,
                         EmailNotifier emailNotifier,
                         @Value("${app.public-url:http://localhost}") String publicUrl) {
        this.alertRepository = alertRepository;
        this.tickerRepository = tickerRepository;
        this.priceWebSocketHandler = priceWebSocketHandler;
        this.objectMapper = objectMapper;
        this.emailNotifier = emailNotifier;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
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
    //
    // @Transactional because this runs on the price-polling thread, which has no database session of its
    // own: without one, reading the alert's lazy ticker/user threw LazyInitializationException, the alert was
    // still marked triggered, and nobody was ever told. The user is told after the change has committed.
    @Transactional
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

                notifyTrigger(alert, currentPrice, triggeredAt);
            }
        }
    }

    // Everything is read here, inside the transaction; the sending happens after it commits.
    private void notifyTrigger(Alert alert, BigDecimal currentPrice, Instant triggeredAt) {
        try {
            String symbol = alert.getTicker().getSymbol();
            Long userId = alert.getUser().getId();
            String json = objectMapper.writeValueAsString(new AlertTriggeredMessage(
                    "ALERT_TRIGGERED", alert.getId(), symbol,
                    alert.getDirection(), alert.getTargetPrice(), currentPrice, triggeredAt));

            String verb = alert.getDirection() == AlertDirection.ABOVE ? "rose above" : "fell below";
            String emailTo = alert.getUser().isEmailVerified() ? alert.getUser().getEmail() : null;
            String subject = symbol + " " + verb + " $" + alert.getTargetPrice().setScale(2, RoundingMode.HALF_UP);
            String body = "Your Meridian price alert fired.\n\n"
                    + symbol + " " + verb + " your target of $" + alert.getTargetPrice().setScale(2, RoundingMode.HALF_UP)
                    + " and is now $" + currentPrice.setScale(2, RoundingMode.HALF_UP) + ".\n\n"
                    + "Open Meridian: " + publicUrl + "\n\n"
                    + "You set this alert on the Alerts page, where you can also remove it.";

            AfterCommit.run(() -> {
                priceWebSocketHandler.broadcastToUser(userId, json);
                emailNotifier.send(emailTo, subject, body);
            });
        } catch (Exception e) {
            log.warn("Failed to notify alert {}", alert.getId(), e);
        }
    }

    private AlertResponse toResponse(Alert alert) {
        return new AlertResponse(
                alert.getId(), alert.getTicker().getSymbol(), alert.getDirection(),
                alert.getTargetPrice(), alert.isTriggered(), alert.getCreatedAt(), alert.getTriggeredAt()
        );
    }
}
