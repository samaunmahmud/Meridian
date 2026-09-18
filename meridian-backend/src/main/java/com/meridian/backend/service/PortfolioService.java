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
import org.springframework.transaction.annotation.Transactional;

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

    public PortfolioService(PortfolioRepository portfolioRepository,
                             HoldingRepository holdingRepository,
                             OrderRepository orderRepository,
                             TickerRepository tickerRepository,
                             PriceHistoryRepository priceHistoryRepository,
                             PortfolioSnapshotRepository portfolioSnapshotRepository,
                             TransactionRepository transactionRepository,
                             PriceWebSocketHandler priceWebSocketHandler,
                             ObjectMapper objectMapper) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.tickerRepository = tickerRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.transactionRepository = transactionRepository;
        this.priceWebSocketHandler = priceWebSocketHandler;
        this.objectMapper = objectMapper;
    }

    // Normally every user already has a portfolio (AuthService creates one at
    // registration) — this fallback just guards against edge cases, e.g. a
    // user created before this feature existed.
    private Portfolio getOrCreatePortfolio(User user) {
        return portfolioRepository.findByUserId(user.getId())
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

        Portfolio portfolio = getOrCreatePortfolio(user);
        OrderKind kind = request.kind() == null ? OrderKind.MARKET : request.kind();
        BigDecimal quantity = request.quantity();

        return switch (kind) {
            case MARKET -> placeMarketOrder(portfolio, ticker, request.type(), quantity);
            case LIMIT -> placePendingOrder(portfolio, ticker, request.type(), OrderKind.LIMIT, quantity, request.limitPrice(), null);
            case STOP_LOSS -> placePendingOrder(portfolio, ticker, request.type(), OrderKind.STOP_LOSS, quantity, null, request.stopPrice());
        };
    }

    private OrderResponse placeMarketOrder(Portfolio portfolio, Ticker ticker, OrderType type, BigDecimal quantity) {
        BigDecimal currentPrice = getCurrentPrice(ticker);
        BigDecimal realizedPnL;

        if (type == OrderType.BUY) {
            realizedPnL = null; // buys never realize a gain/loss
            executeBuy(portfolio, ticker, quantity, currentPrice);
        } else {
            realizedPnL = executeSell(portfolio, ticker, quantity, currentPrice);
        }

        Instant executedAt = Instant.now();
        Order order = new Order(portfolio, ticker, type, quantity, currentPrice, executedAt);
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
        Portfolio portfolio = getOrCreatePortfolio(user);
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
    @Transactional
    public void checkPendingOrders(Ticker ticker, BigDecimal currentPrice) {
        List<Order> pending = orderRepository.findByTickerIdAndStatus(ticker.getId(), OrderStatus.PENDING);

        for (Order order : pending) {
            boolean shouldFill = switch (order.getKind()) {
                case LIMIT -> order.getType() == OrderType.BUY
                        ? currentPrice.compareTo(order.getLimitPrice()) <= 0
                        : currentPrice.compareTo(order.getLimitPrice()) >= 0;
                case STOP_LOSS -> currentPrice.compareTo(order.getStopPrice()) <= 0;
                case MARKET -> false; // MARKET orders never sit PENDING
            };

            if (shouldFill) {
                fillPendingOrder(order, currentPrice);
            }
        }
    }

    private void fillPendingOrder(Order order, BigDecimal fillPrice) {
        releaseReservation(order);

        Portfolio portfolio = order.getPortfolio();
        Ticker ticker = order.getTicker();
        BigDecimal realizedPnL;

        if (order.getType() == OrderType.BUY) {
            realizedPnL = null;
            executeBuy(portfolio, ticker, order.getQuantity(), fillPrice);
        } else {
            realizedPnL = executeSell(portfolio, ticker, order.getQuantity(), fillPrice);
        }

        Instant executedAt = Instant.now();
        order.setPrice(fillPrice);
        order.setExecutedAt(executedAt);
        order.setStatus(OrderStatus.FILLED);
        orderRepository.save(order);

        log.info("Filled pending {} {} order {} for {} at {}", order.getKind(), order.getType(), order.getId(), ticker.getSymbol(), fillPrice);
        broadcastOrderFilled(order);
    }

    private void broadcastOrderFilled(Order order) {
        try {
            OrderFilledMessage message = new OrderFilledMessage(
                    "ORDER_FILLED", order.getId(), order.getTicker().getSymbol(),
                    order.getType(), order.getQuantity(), order.getPrice(), order.getExecutedAt()
            );
            String json = objectMapper.writeValueAsString(message);
            priceWebSocketHandler.broadcastToUser(order.getPortfolio().getUser().getId(), json);
        } catch (Exception e) {
            log.warn("Failed to broadcast order fill for order {}", order.getId(), e);
        }
    }

    // Checked against availableCash (not raw cashBalance) so a MARKET buy
    // can never spend money already earmarked by someone else's pending
    // LIMIT buy order — and, on the pending-fill path, this order's own
    // reservation was already released just before this call runs.
    private void executeBuy(Portfolio portfolio, Ticker ticker, BigDecimal quantity, BigDecimal price) {
        BigDecimal cost = price.multiply(quantity);

        if (portfolio.getAvailableCash().compareTo(cost) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: need " + cost + " but only " + portfolio.getAvailableCash() + " available");
        }

        portfolio.setCashBalance(portfolio.getCashBalance().subtract(cost));
        portfolioRepository.save(portfolio);

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
    }

    // Checked against availableQuantity (not raw quantity) so a MARKET sell
    // can never sell shares already earmarked by someone else's pending
    // LIMIT/STOP_LOSS sell order — and, on the pending-fill path, this
    // order's own reservation was already released just before this call runs.
    private BigDecimal executeSell(Portfolio portfolio, Ticker ticker, BigDecimal quantity, BigDecimal price) {
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
        portfolio.setCashBalance(portfolio.getCashBalance().add(proceeds));
        portfolioRepository.save(portfolio);

        BigDecimal remainingQuantity = holding.getQuantity().subtract(quantity);
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            holdingRepository.delete(holding);
        } else {
            holding.setQuantity(remainingQuantity);
            holdingRepository.save(holding);
        }

        return realizedPnL;
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
                order.getQuantity(), order.getLimitPrice(), order.getStopPrice(), order.getPrice(),
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

        Portfolio portfolio = getOrCreatePortfolio(user);
        portfolio.setCashBalance(portfolio.getCashBalance().add(amount));
        portfolioRepository.save(portfolio);

        Transaction transaction = transactionRepository.save(
                new Transaction(portfolio, TransactionType.DEPOSIT, amount, portfolio.getCashBalance()));
        return toTransactionResponse(transaction);
    }

    @Transactional
    public TransactionResponse withdraw(BigDecimal amount, User user) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Withdrawal amount must be greater than zero");
        }

        Portfolio portfolio = getOrCreatePortfolio(user);
        if (portfolio.getAvailableCash().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient available funds: need " + amount + " but only "
                            + portfolio.getAvailableCash() + " available (rest reserved by open orders)");
        }

        portfolio.setCashBalance(portfolio.getCashBalance().subtract(amount));
        portfolioRepository.save(portfolio);

        Transaction transaction = transactionRepository.save(
                new Transaction(portfolio, TransactionType.WITHDRAWAL, amount, portfolio.getCashBalance()));
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
                transaction.getBalanceAfter(), transaction.getCreatedAt()
        );
    }
}
