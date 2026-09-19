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
import com.meridian.backend.model.SupportedCurrency;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final FxRateService fxRateService;
    private final PriceWebSocketHandler priceWebSocketHandler;
    private final ObjectMapper objectMapper;
    // Each due order runs in its own transaction: the scheduler thread has no
    // database session of its own (so lazy relations such as the portfolio
    // can only be read inside a transaction), and one order failing must not
    // roll back the others.
    private final TransactionTemplate perOrderTransaction;

    public RecurringOrderService(RecurringOrderRepository recurringOrderRepository,
                                  PortfolioRepository portfolioRepository,
                                  TickerRepository tickerRepository,
                                  PriceHistoryRepository priceHistoryRepository,
                                  PortfolioService portfolioService,
                                  FxRateService fxRateService,
                                  PriceWebSocketHandler priceWebSocketHandler,
                                  ObjectMapper objectMapper,
                                  PlatformTransactionManager transactionManager) {
        this.recurringOrderRepository = recurringOrderRepository;
        this.portfolioRepository = portfolioRepository;
        this.tickerRepository = tickerRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.portfolioService = portfolioService;
        this.fxRateService = fxRateService;
        this.priceWebSocketHandler = priceWebSocketHandler;
        this.objectMapper = objectMapper;
        this.perOrderTransaction = new TransactionTemplate(transactionManager);
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
                new RecurringOrder(portfolio, ticker, request.amount(), request.settlementCurrency(),
                        request.frequency(), Instant.now()));
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

    // What to tell the user's browser once an execution has committed.
    private record Executed(Long userId, Long recurringOrderId, OrderResponse order) {
    }

    // Called by the scheduler. Each due order is executed in its own
    // transaction (see perOrderTransaction), so one failing (e.g. insufficient
    // funds) can't undo the others, and the order it places and the advance of
    // its next run time commit together or not at all. The websocket message
    // goes out only after the commit.
    public void runDue() {
        List<Long> dueIds = recurringOrderRepository.findByActiveTrueAndNextRunAtLessThanEqual(Instant.now())
                .stream().map(RecurringOrder::getId).toList();
        for (Long id : dueIds) {
            try {
                Executed executed = perOrderTransaction.execute(tx -> executeOne(id));
                if (executed != null) {
                    broadcastExecuted(executed);
                }
            } catch (Exception e) {
                log.warn("Recurring order {} failed to execute", id, e);
            }
        }
    }

    private Executed executeOne(Long id) {
        RecurringOrder recurringOrder = recurringOrderRepository.findById(id).orElse(null);
        if (recurringOrder == null || !recurringOrder.isActive()) {
            return null;
        }

        Ticker ticker = recurringOrder.getTicker();
        BigDecimal price = priceHistoryRepository.findFirstByTickerIdOrderByRecordedAtDesc(ticker.getId())
                .map(PriceHistory::getPrice)
                .orElse(null);

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("No price available for recurring order {} ({})", recurringOrder.getId(), ticker.getSymbol());
            return null;
        }

        // The amount is in the wallet's currency; shares are priced in USD.
        SupportedCurrency currency = recurringOrder.getSettlementCurrency();
        BigDecimal amountUsd = recurringOrder.getAmount().multiply(fxRateService.getRate(currency, SupportedCurrency.USD));
        BigDecimal quantity = amountUsd.divide(price, 4, RoundingMode.HALF_UP);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        User user = recurringOrder.getPortfolio().getUser();
        OrderRequest orderRequest = new OrderRequest(ticker.getSymbol(), OrderType.BUY, OrderKind.MARKET, quantity, null, null, currency);
        OrderResponse order = portfolioService.placeOrder(orderRequest, user);

        recurringOrder.advanceNextRun();
        recurringOrderRepository.save(recurringOrder);

        return new Executed(user.getId(), recurringOrder.getId(), order);
    }

    private void broadcastExecuted(Executed executed) {
        try {
            OrderResponse order = executed.order();
            RecurringOrderExecutedMessage message = new RecurringOrderExecutedMessage(
                    "RECURRING_ORDER_EXECUTED", executed.recurringOrderId(), order.symbol(), order.quantity(),
                    order.price(), order.executedAt());
            priceWebSocketHandler.broadcastToUser(executed.userId(), objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            log.warn("Failed to broadcast recurring order execution for {}", executed.recurringOrderId(), e);
        }
    }

    private RecurringOrderResponse toResponse(RecurringOrder recurringOrder) {
        return new RecurringOrderResponse(
                recurringOrder.getId(), recurringOrder.getTicker().getSymbol(), recurringOrder.getAmount(),
                recurringOrder.getSettlementCurrency(), recurringOrder.getFrequency(), recurringOrder.getNextRunAt(), recurringOrder.isActive(),
                recurringOrder.getCreatedAt()
        );
    }
}
