package com.meridian.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.dto.RecurringOrderExecutedMessage;
import com.meridian.backend.dto.RecurringOrderRequest;
import com.meridian.backend.dto.RecurringOrderResponse;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.exception.RecurringOrderNotFoundException;
import com.meridian.backend.exception.TickerNotFoundException;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.Portfolio;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.RecurringOrder;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.repository.RecurringOrderRepository;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.websocket.PriceWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
public class RecurringOrderService {

    private static final Logger log = LoggerFactory.getLogger(RecurringOrderService.class);

    private final RecurringOrderRepository recurringOrderRepository;
    private final PortfolioRepository portfolioRepository;
    private final TickerRepository tickerRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PortfolioService portfolioService;
    private final PriceWebSocketHandler priceWebSocketHandler;
    private final ObjectMapper objectMapper;

    public RecurringOrderService(RecurringOrderRepository recurringOrderRepository,
                                  PortfolioRepository portfolioRepository,
                                  TickerRepository tickerRepository,
                                  PriceHistoryRepository priceHistoryRepository,
                                  PortfolioService portfolioService,
                                  PriceWebSocketHandler priceWebSocketHandler,
                                  ObjectMapper objectMapper) {
        this.recurringOrderRepository = recurringOrderRepository;
        this.portfolioRepository = portfolioRepository;
        this.tickerRepository = tickerRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.portfolioService = portfolioService;
        this.priceWebSocketHandler = priceWebSocketHandler;
        this.objectMapper = objectMapper;
    }

    private Portfolio requirePortfolio(User user) {
        return portfolioRepository.findByUserId(user.getId())
                .orElseThrow(() -> new InvalidRequestException("No portfolio found for user"));
    }

    public RecurringOrderResponse create(RecurringOrderRequest request, User user) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Amount must be greater than zero");
        }
        if (request.frequency() == null) {
            throw new InvalidRequestException("Frequency is required");
        }

        Ticker ticker = tickerRepository.findBySymbol(request.symbol())
                .orElseThrow(() -> new TickerNotFoundException(request.symbol()));
        Portfolio portfolio = requirePortfolio(user);

        RecurringOrder recurringOrder = recurringOrderRepository.save(
                new RecurringOrder(portfolio, ticker, request.amount(), request.frequency(), Instant.now()));
        return toResponse(recurringOrder);
    }

    public List<RecurringOrderResponse> getRecurringOrders(User user) {
        Portfolio portfolio = requirePortfolio(user);
        return recurringOrderRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolio.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public void cancel(Long id, User user) {
        Portfolio portfolio = requirePortfolio(user);
        RecurringOrder recurringOrder = recurringOrderRepository.findByIdAndPortfolioId(id, portfolio.getId())
                .orElseThrow(() -> new RecurringOrderNotFoundException(id));
        recurringOrderRepository.delete(recurringOrder);
    }

    // Called by the scheduler. Deliberately NOT @Transactional at this
    // level: each order's execution (a call into PortfolioService.placeOrder,
    // itself @Transactional) must run as its own independent transaction, so
    // one order failing (e.g. insufficient funds) can't mark a shared
    // transaction rollback-only and silently undo every other order in the
    // same batch.
    public void runDue() {
        List<RecurringOrder> due = recurringOrderRepository.findByActiveTrueAndNextRunAtLessThanEqual(Instant.now());
        for (RecurringOrder recurringOrder : due) {
            try {
                executeOne(recurringOrder);
            } catch (Exception e) {
                log.warn("Recurring order {} failed to execute", recurringOrder.getId(), e);
            }
        }
    }

    private void executeOne(RecurringOrder recurringOrder) {
        Ticker ticker = recurringOrder.getTicker();
        BigDecimal price = priceHistoryRepository.findFirstByTickerIdOrderByRecordedAtDesc(ticker.getId())
                .map(PriceHistory::getPrice)
                .orElse(null);

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("No price available for recurring order {} ({})", recurringOrder.getId(), ticker.getSymbol());
            return;
        }

        BigDecimal quantity = recurringOrder.getAmount().divide(price, 4, RoundingMode.HALF_UP);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        User user = recurringOrder.getPortfolio().getUser();
        OrderRequest orderRequest = new OrderRequest(ticker.getSymbol(), OrderType.BUY, OrderKind.MARKET, quantity, null, null);
        OrderResponse order = portfolioService.placeOrder(orderRequest, user);

        recurringOrder.advanceNextRun();
        recurringOrderRepository.save(recurringOrder);

        broadcastExecuted(recurringOrder, order);
    }

    private void broadcastExecuted(RecurringOrder recurringOrder, OrderResponse order) {
        try {
            RecurringOrderExecutedMessage message = new RecurringOrderExecutedMessage(
                    "RECURRING_ORDER_EXECUTED", recurringOrder.getId(), order.symbol(), order.quantity(),
                    order.price(), order.executedAt());
            String json = objectMapper.writeValueAsString(message);
            priceWebSocketHandler.broadcastToUser(recurringOrder.getPortfolio().getUser().getId(), json);
        } catch (Exception e) {
            log.warn("Failed to broadcast recurring order execution for {}", recurringOrder.getId(), e);
        }
    }

    private RecurringOrderResponse toResponse(RecurringOrder recurringOrder) {
        return new RecurringOrderResponse(
                recurringOrder.getId(), recurringOrder.getTicker().getSymbol(), recurringOrder.getAmount(),
                recurringOrder.getFrequency(), recurringOrder.getNextRunAt(), recurringOrder.isActive(),
                recurringOrder.getCreatedAt()
        );
    }
}
