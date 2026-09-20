package com.meridian.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.backend.dto.HoldingResponse;
import com.meridian.backend.mail.EmailNotifier;
import com.meridian.backend.market.MarketCalendar;
import com.meridian.backend.dto.OrderFilledMessage;
import com.meridian.backend.dto.OrderRejectedMessage;
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
    // Extra cash held for a market buy queued while the market is closed (the price can gap at the open).
    private static final BigDecimal QUEUED_BUY_CUSHION = new BigDecimal("0.10");

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
    private final WalletService walletService;
    private final FxRateService fxRateService;
    private final PriceFreshness priceFreshness;
    private final MarketCalendar marketCalendar;
    private final EmailNotifier emailNotifier;
    private final String publicUrl;
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
                             WalletService walletService,
                             FxRateService fxRateService,
                             PriceFreshness priceFreshness,
                             MarketCalendar marketCalendar,
                             EmailNotifier emailNotifier,
                             @org.springframework.beans.factory.annotation.Value("${app.public-url:http://localhost}") String publicUrl,
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
        this.walletService = walletService;
        this.fxRateService = fxRateService;
        this.priceFreshness = priceFreshness;
        this.marketCalendar = marketCalendar;
        this.emailNotifier = emailNotifier;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.perOrderTransaction = new TransactionTemplate(transactionManager);
        this.perOrderTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Bundles the two things a sell needs to report back that a buy doesn't:
    // realized P&L and the commission charged.
    private record TradeExecution(BigDecimal realizedPnL, BigDecimal fee, BigDecimal settlementAmount) {
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

    // The price a market order executes at. Unlike getCurrentPrice (used to show what a
    // holding is worth, where the last known price is the right answer), this refuses a
    // price the feed has stopped refreshing: filling at hours-old data is not a real fill.
    private BigDecimal getTradablePrice(Ticker ticker) {
        PriceHistory latest = priceHistoryRepository.findFirstByTickerIdOrderByRecordedAtDesc(ticker.getId())
                .orElseThrow(() -> new PriceUnavailableException(ticker.getSymbol()));
        priceFreshness.requireFreshPrice(ticker, latest.getRecordedAt());
        return latest.getPrice();
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

        SupportedCurrency settlement = request.settlementCurrency() == null ? SupportedCurrency.USD : request.settlementCurrency();

        return switch (kind) {
            // While a stock market is closed a market order is queued and fills at the open.
            case MARKET -> marketCalendar.isOpen(ticker.getAssetType())
                    ? placeMarketOrder(portfolio, ticker, request.type(), quantity, settlement)
                    : placePendingOrder(portfolio, ticker, request.type(), OrderKind.MARKET, quantity, null, null, settlement);
            case LIMIT -> placePendingOrder(portfolio, ticker, request.type(), OrderKind.LIMIT, quantity, request.limitPrice(), null, settlement);
            case STOP_LOSS -> placePendingOrder(portfolio, ticker, request.type(), OrderKind.STOP_LOSS, quantity, null, request.stopPrice(), settlement);
        };
    }

    // The order is saved first (so BUY/SELL/FEE transactions can reference
    // its id) inside this @Transactional method — if execution then throws
    // (e.g. insufficient funds), the whole transaction rolls back and no
    // order row survives.
    private OrderResponse placeMarketOrder(Portfolio portfolio, Ticker ticker, OrderType type, BigDecimal quantity,
                                            SupportedCurrency settlement) {
        BigDecimal currentPrice = getTradablePrice(ticker);
        Instant executedAt = Instant.now();
        Order order = new Order(portfolio, ticker, type, quantity, currentPrice, executedAt);
        order = orderRepository.save(order);

        TradeExecution execution = type == OrderType.BUY
                ? executeBuy(portfolio, ticker, quantity, currentPrice, order.getId(), settlement)
                : executeSell(portfolio, ticker, quantity, currentPrice, order.getId(), settlement);

        order.setFeeAmount(execution.fee());
        if (settlement != SupportedCurrency.USD) {
            order.setSettlementCurrency(settlement);
            order.setSettlementAmount(execution.settlementAmount());
        }
        order = orderRepository.save(order);

        return toResponse(order, execution.realizedPnL());
    }

    // LIMIT and STOP_LOSS orders don't fill immediately — they sit PENDING
    // until checkPendingOrders() sees a matching price. The funds/shares
    // they'd need are reserved right away so they can't also be promised to
    // a different order placed in the meantime.
    //
    // `settlement` is the wallet the order pays from (buy) or into (sell) when
    // it fills. A buy reserves its money in that wallet's own currency, worked
    // out at today's exchange rate; if the rate moves against the user before
    // the order fills and the wallet can no longer cover it, the fill is
    // rejected (see checkPendingOrders) rather than overdrawing the wallet.
    private OrderResponse placePendingOrder(Portfolio portfolio, Ticker ticker, OrderType type, OrderKind kind,
                                             BigDecimal quantity, BigDecimal limitPrice, BigDecimal stopPrice,
                                             SupportedCurrency settlement) {
        if (kind == OrderKind.LIMIT) {
            if (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidRequestException("A limit order requires a positive limit price");
            }
        } else if (kind == OrderKind.STOP_LOSS) {
            if (stopPrice == null || stopPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidRequestException("A stop-loss order requires a positive stop price");
            }
            if (type != OrderType.SELL) {
                throw new InvalidRequestException("Stop-loss orders are only supported on the sell side");
            }
        }

        BigDecimal reservedAmount = null;
        if (type == OrderType.BUY) {
            // Hold the price AND the commission. The order can fill at any
            // price up to the limit, and the fee only grows with the price,
            // so notional + fee at the limit is the most it can ever cost.
            // (Reserving just the price left orders that used all the user's
            // cash unable to ever pay their own fee.)
            // A queued market order has no limit: hold the last price plus a cushion for the
            // opening gap. Whatever is not needed is released when it fills, and if the open
            // is beyond the cushion the fill is rejected rather than overdrawing (see checkPendingOrders).
            BigDecimal basis = kind == OrderKind.MARKET
                    ? getCurrentPrice(ticker).multiply(BigDecimal.ONE.add(QUEUED_BUY_CUSHION))
                    : limitPrice;
            BigDecimal notional = basis.multiply(quantity);
            BigDecimal totalUsd = notional.add(feeService.commissionFor(notional));
            if (settlement == SupportedCurrency.USD) {
                reservedAmount = totalUsd;
                if (portfolio.getAvailableCash().compareTo(reservedAmount) < 0) {
                    throw new InsufficientFundsException(
                            "Insufficient funds: need " + reservedAmount + " (including fees) but only "
                                    + portfolio.getAvailableCash() + " available");
                }
                portfolio.setReservedCash(portfolio.getReservedCash().add(reservedAmount));
                portfolioRepository.save(portfolio);
            } else {
                // Rounded up, exactly as the fill will round it.
                reservedAmount = totalUsd.divide(usdPerUnit(settlement), 4, RoundingMode.UP);
                walletService.reserve(portfolio, settlement, reservedAmount);
            }
        } else {
            // Nothing to reserve in cash, but fail now (not at fill time) if
            // the wallet's exchange rate isn't known.
            if (settlement != SupportedCurrency.USD) {
                usdPerUnit(settlement);
            }
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
        order.setReservedAmount(reservedAmount); // in the settlement currency
        if (settlement != SupportedCurrency.USD) {
            order.setSettlementCurrency(settlement);
        }
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
            SupportedCurrency currency = settlementOf(order);
            if (currency == SupportedCurrency.USD) {
                // Orders placed before reservedAmount existed only held price * quantity.
                BigDecimal held = order.getReservedAmount() != null
                        ? order.getReservedAmount()
                        : order.getLimitPrice().multiply(order.getQuantity());
                portfolio.setReservedCash(portfolio.getReservedCash().subtract(held).max(BigDecimal.ZERO));
                portfolioRepository.save(portfolio);
            } else {
                walletService.release(portfolio, currency, order.getReservedAmount());
            }
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
        // Outside the trading session the provider just repeats the last close: nothing fills on it.
        if (!marketCalendar.isOpen(ticker.getAssetType())) {
            return;
        }
        List<Long> pendingIds = orderRepository.findIdsByTickerIdAndStatus(ticker.getId(), OrderStatus.PENDING);

        for (Long orderId : pendingIds) {
            try {
                UserNotice notice = perOrderTransaction.execute(tx -> tryFill(orderId, currentPrice));
                if (notice != null) {
                    sendToUser(notice);
                }
            } catch (InsufficientFundsException | InsufficientSharesException | InvalidRequestException e) {
                // A permanent problem (the order can no longer be paid for):
                // reject it and free what it reserved, instead of retrying
                // it on every price tick forever.
                log.warn("Rejecting pending order {}: {}", orderId, e.getMessage());
                rejectOrder(orderId, e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Pending order {} could not be filled; will retry on the next price update", orderId, e);
            }
        }
    }

    // What to tell the user's browser once a change has committed. Built
    // inside the transaction (lazy relations are only readable there) and
    // sent after it, so nobody is told about something that later rolled back.
    // `emailTo` is set only for a confirmed address; the email covers what happens while nobody is looking.
    private record UserNotice(Long userId, Object message, String emailTo, String emailSubject, String emailBody) {
        UserNotice(Long userId, Object message) {
            this(userId, message, null, null, null);
        }
    }

    private String confirmedEmailOf(Order order) {
        var user = order.getPortfolio().getUser();
        return user.isEmailVerified() ? user.getEmail() : null;
    }

    private String describeOrder(Order order) {
        String side = order.getType() == OrderType.BUY ? "buy" : "sell";
        return side + " " + order.getQuantity().stripTrailingZeros().toPlainString() + " " + order.getTicker().getSymbol();
    }

    private void rejectOrder(Long orderId, String reason) {
        try {
            UserNotice notice = perOrderTransaction.execute(tx -> {
                Long portfolioId = orderRepository.findPortfolioIdById(orderId).orElse(null);
                if (portfolioId == null) return null;
                portfolioRepository.findByIdForUpdate(portfolioId).orElseThrow();
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order == null || order.getStatus() != OrderStatus.PENDING) return null;

                releaseReservation(order);
                String shortReason = reason == null ? "Order could not be filled"
                        : reason.substring(0, Math.min(reason.length(), 250));
                order.setStatus(OrderStatus.REJECTED);
                order.setRejectionReason(shortReason);
                orderRepository.save(order);
                return new UserNotice(order.getPortfolio().getUser().getId(), new OrderRejectedMessage(
                        "ORDER_REJECTED", order.getId(), order.getTicker().getSymbol(),
                        order.getType(), order.getQuantity(), shortReason),
                        confirmedEmailOf(order),
                        "Order rejected: " + describeOrder(order),
                        "Your order to " + describeOrder(order) + " could not be filled: " + shortReason + "\n\n"
                                + "The money or shares it was holding have been released.\n"
                                + "See your orders in Meridian: " + publicUrl);
            });
            if (notice != null) {
                sendToUser(notice);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to reject pending order {}", orderId, e);
        }
    }

    private UserNotice tryFill(Long orderId, BigDecimal currentPrice) {
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
            case MARKET -> true; // queued while the market was closed: fills at the first price after the open
        };
        if (!shouldFill) return null;

        fillPendingOrder(order, currentPrice);
        String kindWords = switch (order.getKind()) {
            case MARKET -> "queued market order";
            case LIMIT -> "limit order";
            case STOP_LOSS -> "stop-loss order";
        };
        String price = order.getPrice().setScale(2, RoundingMode.HALF_UP).toPlainString();
        return new UserNotice(order.getPortfolio().getUser().getId(), new OrderFilledMessage(
                "ORDER_FILLED", order.getId(), order.getTicker().getSymbol(),
                order.getType(), order.getQuantity(), order.getPrice(), order.getExecutedAt()),
                confirmedEmailOf(order),
                "Order filled: " + describeOrder(order) + " @ $" + price,
                "Your " + kindWords + " to " + describeOrder(order) + " was filled at $" + price + ".\n\n"
                        + "See it in Meridian: " + publicUrl);
    }

    private void fillPendingOrder(Order order, BigDecimal fillPrice) {
        releaseReservation(order);

        Portfolio portfolio = order.getPortfolio();
        Ticker ticker = order.getTicker();

        // Settles in the wallet chosen when the order was placed.
        SupportedCurrency settlement = settlementOf(order);
        TradeExecution execution = order.getType() == OrderType.BUY
                ? executeBuy(portfolio, ticker, order.getQuantity(), fillPrice, order.getId(), settlement)
                : executeSell(portfolio, ticker, order.getQuantity(), fillPrice, order.getId(), settlement);

        order.setPrice(fillPrice);
        order.setFeeAmount(execution.fee());
        if (settlement != SupportedCurrency.USD) {
            order.setSettlementAmount(execution.settlementAmount()); // what actually moved, at the fill-time rate
        }
        order.setExecutedAt(Instant.now());
        order.setStatus(OrderStatus.FILLED);
        orderRepository.save(order);

        log.info("Filled pending {} {} order {} for {} at {}", order.getKind(), order.getType(), order.getId(), ticker.getSymbol(), fillPrice);
    }

    // Orders that settled in USD before settlement currencies existed have none stored.
    private static SupportedCurrency settlementOf(Order order) {
        return order.getSettlementCurrency() == null ? SupportedCurrency.USD : order.getSettlementCurrency();
    }

    // USD received for 1 unit of `currency` after the exchange spread — the
    // rate every trade paid from a non-USD wallet is converted at.
    private BigDecimal usdPerUnit(SupportedCurrency currency) {
        return fxRateService.getRate(currency, SupportedCurrency.USD)
                .multiply(BigDecimal.ONE.subtract(FeeService.FX_SPREAD));
    }

    private void sendToUser(UserNotice notice) {
        try {
            priceWebSocketHandler.broadcastToUser(notice.userId(), objectMapper.writeValueAsString(notice.message()));
        } catch (Exception e) {
            log.warn("Failed to send order notification to user {}", notice.userId(), e);
        }
        emailNotifier.send(notice.emailTo(), notice.emailSubject(), notice.emailBody());
    }

    // USD trades settle from the portfolio's cash, checked against availableCash
    // (not raw cashBalance) so a MARKET buy can never spend money already
    // earmarked by someone else's pending LIMIT buy — and, on the pending-fill
    // path, this order's own reservation was already released just before this
    // call runs.
    //
    // A non-USD `settlement` pays from that currency's wallet instead. The USD
    // total is converted at the live rate minus the same 0.5% spread as a
    // manual conversion, so trading straight from a EUR wallet costs exactly
    // what converting first and then buying would. The amount charged is
    // rounded up, never down, so the spread can't be rounded away.
    private TradeExecution executeBuy(Portfolio portfolio, Ticker ticker, BigDecimal quantity, BigDecimal price,
                                      Long orderId, SupportedCurrency settlement) {
        BigDecimal cost = price.multiply(quantity);
        BigDecimal fee = feeService.commissionFor(cost);
        BigDecimal totalDebit = cost.add(fee);
        BigDecimal settled;

        if (settlement == SupportedCurrency.USD) {
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
            settled = totalDebit;
        } else {
            BigDecimal usdPerUnit = usdPerUnit(settlement);
            BigDecimal totalPaid = totalDebit.divide(usdPerUnit, 4, RoundingMode.UP);
            BigDecimal feePaid = fee.divide(usdPerUnit, 4, RoundingMode.HALF_UP);
            BigDecimal costPaid = totalPaid.subtract(feePaid);

            BigDecimal afterFee = walletService.debitWallet(portfolio, settlement, totalPaid);
            BigDecimal afterCost = afterFee.add(feePaid);

            String via = " (paid from " + settlement + " wallet)";
            transactionRepository.save(new Transaction(portfolio, TransactionType.BUY, costPaid.negate(), afterCost,
                    settlement.name(), "Bought " + quantity + " " + ticker.getSymbol() + via, orderId));
            transactionRepository.save(new Transaction(portfolio, TransactionType.FEE, feePaid.negate(), afterFee,
                    settlement.name(), "Commission on " + ticker.getSymbol() + " order", orderId));
            settled = totalPaid;
        }

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

        return new TradeExecution(null, fee, settled); // buys never realize a gain/loss
    }

    // Checked against availableQuantity (not raw quantity) so a MARKET sell
    // can never sell shares already earmarked by someone else's pending
    // LIMIT/STOP_LOSS sell order — and, on the pending-fill path, this
    // order's own reservation was already released just before this call runs.
    //
    // With a non-USD `settlement` the proceeds (after commission) are
    // converted at the live rate minus the 0.5% spread and credited to that
    // wallet, rounded down.
    private TradeExecution executeSell(Portfolio portfolio, Ticker ticker, BigDecimal quantity, BigDecimal price,
                                       Long orderId, SupportedCurrency settlement) {
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
        BigDecimal settled;

        if (settlement == SupportedCurrency.USD) {
            BigDecimal afterProceeds = portfolio.getCashBalance().add(proceeds);
            BigDecimal afterFee = afterProceeds.subtract(fee);
            portfolio.setCashBalance(afterFee);
            portfolioRepository.save(portfolio);

            transactionRepository.save(new Transaction(portfolio, TransactionType.SELL, proceeds, afterProceeds,
                    "USD", "Sold " + quantity + " " + ticker.getSymbol(), orderId));
            transactionRepository.save(new Transaction(portfolio, TransactionType.FEE, fee.negate(), afterFee,
                    "USD", "Commission on " + ticker.getSymbol() + " order", orderId));
            settled = proceeds.subtract(fee);
        } else {
            BigDecimal netUsd = proceeds.subtract(fee);
            if (netUsd.signum() <= 0) {
                throw new InvalidRequestException(
                        "This sale is worth less than its commission; sell it into your USD balance instead");
            }
            BigDecimal rawRate = fxRateService.getRate(settlement, SupportedCurrency.USD);
            BigDecimal keep = BigDecimal.ONE.subtract(FeeService.FX_SPREAD);
            BigDecimal credited = netUsd.multiply(keep).divide(rawRate, 4, RoundingMode.DOWN);
            BigDecimal feeInWallet = fee.multiply(keep).divide(rawRate, 4, RoundingMode.HALF_UP);
            BigDecimal proceedsInWallet = credited.add(feeInWallet);

            BigDecimal afterFee = walletService.creditWallet(portfolio, settlement, credited);
            BigDecimal afterProceeds = afterFee.add(feeInWallet);

            String via = " (paid into " + settlement + " wallet)";
            transactionRepository.save(new Transaction(portfolio, TransactionType.SELL, proceedsInWallet, afterProceeds,
                    settlement.name(), "Sold " + quantity + " " + ticker.getSymbol() + via, orderId));
            transactionRepository.save(new Transaction(portfolio, TransactionType.FEE, feeInWallet.negate(), afterFee,
                    settlement.name(), "Commission on " + ticker.getSymbol() + " order", orderId));
            settled = credited;
        }

        BigDecimal remainingQuantity = holding.getQuantity().subtract(quantity);
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            holdingRepository.delete(holding);
        } else {
            holding.setQuantity(remainingQuantity);
            holdingRepository.save(holding);
        }

        return new TradeExecution(realizedPnL, fee, settled);
    }

    // Reads lazy relations (each holding's ticker), so it needs a database session. A web
    // request has one for its whole duration, but the snapshot scheduler runs on a thread
    // that does not, and this used to throw LazyInitializationException every minute for
    // anyone who held a stock, so their equity curve was never recorded.
    // (Not readOnly: getOrCreatePortfolio can insert a missing portfolio for an old account.)
    @Transactional
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
                order.getCreatedAt(), order.getExecutedAt(), realizedPnL, order.getRejectionReason(),
                order.getSettlementCurrency(), order.getSettlementAmount()
        );
    }

    // Real historical total-value points, oldest first — exactly what the
    // frontend needs to draw a genuine equity curve.
    public static final int DEFAULT_HISTORY_POINTS = 500;

    public List<PortfolioSnapshotResponse> getPortfolioHistory(User user) {
        return getPortfolioHistory(user, null);
    }

    /** Oldest first, at most {@code points} snapshots (default 500) spread evenly over the whole history. */
    public List<PortfolioSnapshotResponse> getPortfolioHistory(User user, Integer points) {
        int max = points == null ? DEFAULT_HISTORY_POINTS : points;
        if (max < 2 || max > 5000) {
            throw new InvalidRequestException("points must be between 2 and 5000");
        }
        Portfolio portfolio = getOrCreatePortfolio(user);
        return Downsample.evenly(portfolioSnapshotRepository.findByPortfolioIdOrderByRecordedAtAsc(portfolio.getId()), max).stream()
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
