package com.meridian.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.backend.dto.HoldingResponse;
import com.meridian.backend.dto.OrderFilledMessage;
import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.dto.PortfolioResponse;
import com.meridian.backend.dto.PortfolioSnapshotResponse;
import com.meridian.backend.dto.TransactionResponse;
import com.meridian.backend.exception.InsufficientFundsException;
import com.meridian.backend.exception.InsufficientSharesException;
import com.meridian.backend.exception.InvalidOrderStateException;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.exception.OrderNotFoundException;
import com.meridian.backend.exception.PriceUnavailableException;
import com.meridian.backend.exception.TickerNotFoundException;
import com.meridian.backend.model.*;
import com.meridian.backend.repository.HoldingRepository;
import com.meridian.backend.repository.OrderRepository;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.PortfolioSnapshotRepository;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.repository.TransactionRepository;
import com.meridian.backend.websocket.PriceWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private static final BigDecimal STARTING_CASH = new BigDecimal("10000.00");

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;
    private final TickerRepository tickerRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final TransactionRepository transactionRepository;
    private final PriceWebSocketHandler priceWebSocketHandler;
    private final ObjectMapper objectMapper;
    private final FeeService feeService;
    // Runs one pending-order fill in its own transaction, so a failure on one
    // order can never roll back (and block) the fills of other orders.
    private final TransactionTemplate perOrderTransaction;

    public PortfolioService(PortfolioRepository portfolioRepository,
                             HoldingRepository holdingRepository,
                             OrderRepository orderRepository,
                             TickerRepository tickerRepository,
                             PriceHistoryRepository priceHistoryRepository,
                             PortfolioSnapshotRepository portfolioSnapshotRepository,
                             TransactionRepository transactionRepository,
                             PriceWebSocketHandler priceWebSocketHandler,
                             ObjectMapper objectMapper,
                             FeeService feeService,
                             PlatformTransactionManager transactionManager) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.tickerRepository = tickerRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.transactionRepository = transactionRepository;
        this.priceWebSocketHandler = priceWebSocketHandler;
        this.objectMapper = objectMapper;
        this.feeService = feeService;
        this.perOrderTransaction = new TransactionTemplate(transactionManager);
        this.perOrderTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Bundles the two things a sell needs to report back that a buy doesn't:
    // realized P&L and the commission charged.
    private record TradeExecution(BigDecimal realizedPnL, BigDecimal fee) {
    }

    // Normally every user already has a portfolio (AuthService creates one at
    // registration) — this fallback just guards against edge cases, e.g. a
    // user created before this feature existed.
    private Portfolio getOrCreatePortfolio(User user) {
        return portfolioRepository.findByUserId(user.getId())
                .orElseGet(() -> portfolioRepository.save(new Portfolio(user, STARTING_CASH)));
    }

    // Same as getOrCreatePortfolio, but takes a row lock held until the
    // surrounding transaction ends — use this in anything that writes money
    // or shares. See PortfolioRepository.findByUserIdForUpdate.
    private Portfolio lockPortfolio(User user) {
        return portfolioRepository.findByUserIdForUpdate(user.getId())
                .orElseGet(() -> portfolioRepository.save(new Portfolio(user, STARTING_CASH)));
    }

    private BigDecimal getCurrentPrice(Ticker ticker) {
        return priceHistoryRepository.findFirstByTickerIdOrderByRecordedAtDesc(ticker.getId())
                .map(PriceHistory::getPrice)
                .orElseThrow(() -> new PriceUnavailableException(ticker.getSymbol()));
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request, User user) {
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Quantity must be greater than zero");
        }

        Ticker ticker = tickerRepository.findBySymbol(request.symbol())
                .orElseThrow(() -> new TickerNotFoundException(request.symbol()));

        Portfolio portfolio = lockPortfolio(user);
        OrderKind kind = request.kind() == null ? OrderKind.MARKET : request.kind();
        BigDecimal quantity = request.quantity();

        return switch (kind) {
            case MARKET -> placeMarketOrder(portfolio, ticker, request.type(), quantity);
            case LIMIT -> placePendingOrder(portfolio, ticker, request.type(), OrderKind.LIMIT, quantity, request.limitPrice(), null);
            case STOP_LOSS -> placePendingOrder(portfolio, ticker, request.type(), OrderKind.STOP_LOSS, quantity, null, request.stopPrice());
        };
    }

    // The order is saved first (so BUY/SELL/FEE transactions can reference
    // its id) inside this @Transactional method — if execution then throws
    // (e.g. insufficient funds), the whole transaction rolls back and no
    // order row survives.
    private OrderResponse placeMarketOrder(Portfolio portfolio, Ticker ticker, OrderType type, BigDecimal quantity) {
        BigDecimal currentPrice = getCurrentPrice(ticker);
        Instant executedAt = Instant.now();
        Order order = new Order(portfolio, ticker, type, quantity, currentPrice, executedAt);
        order = orderRepository.save(order);

        BigDecimal realizedPnL;
        BigDecimal fee;
        if (type == OrderType.BUY) {
            realizedPnL = null; // buys never realize a gain/loss
            fee = executeBuy(portfolio, ticker, quantity, currentPrice, order.getId());
        } else {
            TradeExecution execution = executeSell(portfolio, ticker, quantity, currentPrice, order.getId());
            realizedPnL = execution.realizedPnL();
            fee = execution.fee();
        }

        order.setFeeAmount(fee);
        order = orderRepository.save(order);

        return toResponse(order, realizedPnL);
    }

    // LIMIT and STOP_LOSS orders don't fill immediately — they sit PENDING
    // until checkPendingOrders() sees a matching price. The funds/shares
    // they'd need are reserved right away so they can't also be promised to
    // a different order placed in the meantime.
    private OrderResponse placePendingOrder(Portfolio portfolio, Ticker ticker, OrderType type, OrderKind kind,
                                             BigDecimal quantity, BigDecimal limitPrice, BigDecimal stopPrice) {
        if (kind == OrderKind.LIMIT) {
            if (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidRequestException("A limit order requires a positive limit price");
            }
        } else {
            if (stopPrice == null || stopPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidRequestException("A stop-loss order requires a positive stop price");
            }
            if (type != OrderType.SELL) {
                throw new InvalidRequestException("Stop-loss orders are only supported on the sell side");
            }
        }

        if (type == OrderType.BUY) {
            BigDecimal reservedCost = limitPrice.multiply(quantity);
            if (portfolio.getAvailableCash().compareTo(reservedCost) < 0) {
                throw new InsufficientFundsException(
                        "Insufficient funds: need " + reservedCost + " but only " + portfolio.getAvailableCash() + " available");
            }
            portfolio.setReservedCash(portfolio.getReservedCash().add(reservedCost));
            portfolioRepository.save(portfolio);
        } else {
            Holding holding = holdingRepository.findByPortfolioIdAndTickerId(portfolio.getId(), ticker.getId())
                    .orElseThrow(() -> new InsufficientSharesException(
                            "You don't own any shares of " + ticker.getSymbol()));
            if (holding.getAvailableQuantity().compareTo(quantity) < 0) {
                throw new InsufficientSharesException(
                        "Insufficient shares: trying to sell " + quantity + " but only "
                                + holding.getAvailableQuantity() + " available (rest reserved by other open orders)");
            }
            holding.setReservedQuantity(holding.getReservedQuantity().add(quantity));
            holdingRepository.save(holding);
        }

        Order order = new Order(portfolio, ticker, type, kind, quantity, limitPrice, stopPrice, Instant.now());
        order = orderRepository.save(order);

        return toResponse(order, null);
    }

    @Transactional
    public void cancelOrder(Long orderId, User user) {
        Portfolio portfolio = lockPortfolio(user);
        Order order = orderRepository.findByIdAndPortfolioId(orderId, portfolio.getId())
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Only pending orders can be cancelled");
        }

        releaseReservation(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        orderRepository.save(order);
    }

    private void releaseReservation(Order order) {
        Portfolio portfolio = order.getPortfolio();
        if (order.getType() == OrderType.BUY) {
            BigDecimal reservedCost = order.getLimitPrice().multiply(order.getQuantity());
            portfolio.setReservedCash(portfolio.getReservedCash().subtract(reservedCost));
            portfolioRepository.save(portfolio);
        } else {
            holdingRepository.findByPortfolioIdAndTickerId(portfolio.getId(), order.getTicker().getId())
                    .ifPresent(holding -> {
                        holding.setReservedQuantity(holding.getReservedQuantity().subtract(order.getQuantity()));
                        holdingRepository.save(holding);
                    });
        }
    }

    // Called every time a fresh price is recorded for a ticker. Fills any
    // PENDING order whose condition the new price satisfies, at that price —
    // by the time this condition is true, currentPrice is already at least
    // as good for the trader as the limit/stop they asked for.
    //
    // Deliberately NOT one big transaction: each order is filled in its own
    // transaction, so one order that can't fill (or a failure on one user)
    // never rolls back or blocks everyone else's fills for that ticker.
    public void checkPendingOrders(Ticker ticker, BigDecimal currentPrice) {
        List<Long> pendingIds = orderRepository.findIdsByTickerIdAndStatus(ticker.getId(), OrderStatus.PENDING);

        for (Long orderId : pendingIds) {
            try {
                FillNotice notice = perOrderTransaction.execute(tx -> tryFill(orderId, currentPrice));
                if (notice != null) {
                    broadcastOrderFilled(notice);
                }
            } catch (RuntimeException e) {
                log.warn("Pending order {} could not be filled; will retry on the next price update", orderId, e);
            }
        }
    }

    // What to tell the user's browser once the fill has committed. Built
    // inside the transaction (lazy relations are only readable there) and
    // sent after it, so nobody is told about a fill that later rolled back.
    private record FillNotice(Long userId, OrderFilledMessage message) {
    }

    private FillNotice tryFill(Long orderId, BigDecimal currentPrice) {
        Long portfolioId = orderRepository.findPortfolioIdById(orderId).orElse(null);
        if (portfolioId == null) return null;

        // Lock first, THEN read the order: a cancel or another fill may have
        // changed it since the scheduler listed it as PENDING.
        portfolioRepository.findByIdForUpdate(portfolioId).orElseThrow();
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) return null;

        boolean shouldFill = switch (order.getKind()) {
            case LIMIT -> order.getType() == OrderType.BUY
                    ? currentPrice.compareTo(order.getLimitPrice()) <= 0
                    : currentPrice.compareTo(order.getLimitPrice()) >= 0;
            case STOP_LOSS -> currentPrice.compareTo(order.getStopPrice()) <= 0;
            case MARKET -> false; // MARKET orders never sit PENDING
        };
        if (!shouldFill) return null;

        fillPendingOrder(order, currentPrice);
        return new FillNotice(order.getPortfolio().getUser().getId(), new OrderFilledMessage(
                "ORDER_FILLED", order.getId(), order.getTicker().getSymbol(),
                order.getType(), order.getQuantity(), order.getPrice(), order.getExecutedAt()));
    }

    private void fillPendingOrder(Order order, BigDecimal fillPrice) {
        releaseReservation(order);

        Portfolio portfolio = order.getPortfolio();
        Ticker ticker = order.getTicker();
        BigDecimal fee;

        if (order.getType() == OrderType.BUY) {
            fee = executeBuy(portfolio, ticker, order.getQuantity(), fillPrice, order.getId());
        } else {
            fee = executeSell(portfolio, ticker, order.getQuantity(), fillPrice, order.getId()).fee();
        }

        order.setPrice(fillPrice);
        order.setFeeAmount(fee);
        order.setExecutedAt(Instant.now());
        order.setStatus(OrderStatus.FILLED);
        orderRepository.save(order);

        log.info("Filled pending {} {} order {} for {} at {}", order.getKind(), order.getType(), order.getId(), ticker.getSymbol(), fillPrice);
    }

    private void broadcastOrderFilled(FillNotice notice) {
        try {
            priceWebSocketHandler.broadcastToUser(notice.userId(), objectMapper.writeValueAsString(notice.message()));
        } catch (Exception e) {
            log.warn("Failed to broadcast order fill for order {}", notice.message().orderId(), e);
        }
    }

    // Checked against availableCash (not raw cashBalance) so a MARKET buy
    // can never spend money already earmarked by someone else's pending
    // LIMIT buy order — and, on the pending-fill path, this order's own
    // reservation was already released just before this call runs.
    private BigDecimal executeBuy(Portfolio portfolio, Ticker ticker, BigDecimal quantity, BigDecimal price, Long orderId) {
        BigDecimal cost = price.multiply(quantity);
        BigDecimal fee = feeService.commissionFor(cost);
        BigDecimal totalDebit = cost.add(fee);

        if (portfolio.getAvailableCash().compareTo(totalDebit) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: need " + totalDebit + " (including fees) but only "
                            + portfolio.getAvailableCash() + " available");
        }

        BigDecimal afterCost = portfolio.getCashBalance().subtract(cost);
        BigDecimal afterFee = afterCost.subtract(fee);
        portfolio.setCashBalance(afterFee);
        portfolioRepository.save(portfolio);

        transactionRepository.save(new Transaction(portfolio, TransactionType.BUY, cost.negate(), afterCost,
                "USD", "Bought " + quantity + " " + ticker.getSymbol(), orderId));
        transactionRepository.save(new Transaction(portfolio, TransactionType.FEE, fee.negate(), afterFee,
                "USD", "Commission on " + ticker.getSymbol() + " order", orderId));

        Holding holding = holdingRepository.findByPortfolioIdAndTickerId(portfolio.getId(), ticker.getId())
                .orElse(null);

        if (holding == null) {
            holdingRepository.save(new Holding(portfolio, ticker, quantity, price));
        } else {
            BigDecimal existingCostBasis = holding.getAvgCost().multiply(holding.getQuantity());
            BigDecimal newCostBasis = existingCostBasis.add(cost);
            BigDecimal newQuantity = holding.getQuantity().add(quantity);
            BigDecimal newAvgCost = newCostBasis.divide(newQuantity, 4, RoundingMode.HALF_UP);

            holding.setQuantity(newQuantity);
            holding.setAvgCost(newAvgCost);
            holdingRepository.save(holding);
        }

        return fee;
    }

    // Checked against availableQuantity (not raw quantity) so a MARKET sell
    // can never sell shares already earmarked by someone else's pending
    // LIMIT/STOP_LOSS sell order — and, on the pending-fill path, this
    // order's own reservation was already released just before this call runs.
    private TradeExecution executeSell(Portfolio portfolio, Ticker ticker, BigDecimal quantity, BigDecimal price, Long orderId) {
        Holding holding = holdingRepository.findByPortfolioIdAndTickerId(portfolio.getId(), ticker.getId())
                .orElseThrow(() -> new InsufficientSharesException(
                        "You don't own any shares of " + ticker.getSymbol()));

        if (holding.getAvailableQuantity().compareTo(quantity) < 0) {
            throw new InsufficientSharesException(
                    "Insufficient shares: trying to sell " + quantity + " but only "
                            + holding.getAvailableQuantity() + " available (rest reserved by other open orders)");
        }

        BigDecimal realizedPnL = price.subtract(holding.getAvgCost()).multiply(quantity);

        BigDecimal proceeds = price.multiply(quantity);
        BigDecimal fee = feeService.commissionFor(proceeds);

        BigDecimal afterProceeds = portfolio.getCashBalance().add(proceeds);
        BigDecimal afterFee = afterProceeds.subtract(fee);
        portfolio.setCashBalance(afterFee);
        portfolioRepository.save(portfolio);

        transactionRepository.save(new Transaction(portfolio, TransactionType.SELL, proceeds, afterProceeds,
                "USD", "Sold " + quantity + " " + ticker.getSymbol(), orderId));
        transactionRepository.save(new Transaction(portfolio, TransactionType.FEE, fee.negate(), afterFee,
                "USD", "Commission on " + ticker.getSymbol() + " order", orderId));

        BigDecimal remainingQuantity = holding.getQuantity().subtract(quantity);
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            holdingRepository.delete(holding);
        } else {
            holding.setQuantity(remainingQuantity);
            holdingRepository.save(holding);
        }

        return new TradeExecution(realizedPnL, fee);
    }

    public PortfolioResponse getPortfolioValuation(User user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        List<Holding> holdings = holdingRepository.findByPortfolioId(portfolio.getId());

        List<HoldingResponse> holdingResponses = holdings.stream()
                .map(this::toHoldingResponse)
                .toList();

        BigDecimal holdingsValue = holdingResponses.stream()
                .map(HoldingResponse::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValue = portfolio.getCashBalance().add(holdingsValue);

        return new PortfolioResponse(
                portfolio.getCashBalance(), portfolio.getReservedCash(), portfolio.getAvailableCash(),
                holdingsValue, totalValue, holdingResponses
        );
    }

    private HoldingResponse toHoldingResponse(Holding holding) {
        Ticker ticker = holding.getTicker();
        BigDecimal currentPrice = getCurrentPrice(ticker);
        BigDecimal marketValue = currentPrice.multiply(holding.getQuantity());
        BigDecimal costBasis = holding.getAvgCost().multiply(holding.getQuantity());
        BigDecimal gainLoss = marketValue.subtract(costBasis);
        BigDecimal gainLossPct = costBasis.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : gainLoss.divide(costBasis, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

        return new HoldingResponse(
                ticker.getSymbol(), ticker.getName(), holding.getQuantity(), holding.getReservedQuantity(),
                holding.getAvailableQuantity(), holding.getAvgCost(), currentPrice, marketValue, gainLoss, gainLossPct
        );
    }

    public List<OrderResponse> getOrderHistory(User user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        return orderRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolio.getId()).stream()
                .map(o -> toResponse(o, null))
                .toList();
    }

    public List<OrderResponse> getOpenOrders(User user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        return orderRepository.findByPortfolioIdAndStatusOrderByCreatedAtDesc(portfolio.getId(), OrderStatus.PENDING).stream()
                .map(o -> toResponse(o, null))
                .toList();
    }

    private OrderResponse toResponse(Order order, BigDecimal realizedPnL) {
        return new OrderResponse(
                order.getId(), order.getTicker().getSymbol(), order.getType(), order.getKind(), order.getStatus(),
                order.getQuantity(), order.getLimitPrice(), order.getStopPrice(), order.getPrice(), order.getFeeAmount(),
                order.getCreatedAt(), order.getExecutedAt(), realizedPnL
        );
    }

    // Real historical total-value points, oldest first — exactly what the
    // frontend needs to draw a genuine equity curve.
    public List<PortfolioSnapshotResponse> getPortfolioHistory(User user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        return portfolioSnapshotRepository.findByPortfolioIdOrderByRecordedAtAsc(portfolio.getId()).stream()
                .map(s -> new PortfolioSnapshotResponse(s.getTotalValue(), s.getRecordedAt()))
                .toList();
    }

    @Transactional
    public TransactionResponse deposit(BigDecimal amount, User user) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Deposit amount must be greater than zero");
        }

        Portfolio portfolio = lockPortfolio(user);
        portfolio.setCashBalance(portfolio.getCashBalance().add(amount));
        portfolioRepository.save(portfolio);

        Transaction transaction = transactionRepository.save(
                new Transaction(portfolio, TransactionType.DEPOSIT, amount, portfolio.getCashBalance(),
                        "USD", "Deposit", null));
        return toTransactionResponse(transaction);
    }

    @Transactional
    public TransactionResponse withdraw(BigDecimal amount, User user) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Withdrawal amount must be greater than zero");
        }

        Portfolio portfolio = lockPortfolio(user);
        if (portfolio.getAvailableCash().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient available funds: need " + amount + " but only "
                            + portfolio.getAvailableCash() + " available (rest reserved by open orders)");
        }

        portfolio.setCashBalance(portfolio.getCashBalance().subtract(amount));
        portfolioRepository.save(portfolio);

        Transaction transaction = transactionRepository.save(
                new Transaction(portfolio, TransactionType.WITHDRAWAL, amount, portfolio.getCashBalance(),
                        "USD", "Withdrawal", null));
        return toTransactionResponse(transaction);
    }

    public List<TransactionResponse> getTransactionHistory(User user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        return transactionRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolio.getId()).stream()
                .map(this::toTransactionResponse)
                .toList();
    }

    private TransactionResponse toTransactionResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(), transaction.getType(), transaction.getAmount(),
                transaction.getBalanceAfter(), transaction.getCurrency(), transaction.getDescription(),
                transaction.getRelatedOrderId(), transaction.getCreatedAt()
        );
    }
}
