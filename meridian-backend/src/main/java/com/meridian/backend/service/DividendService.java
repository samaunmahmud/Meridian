package com.meridian.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.backend.client.DividendEvent;
import com.meridian.backend.client.MarketDataProvider;
import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.dto.DividendInfoResponse;
import com.meridian.backend.dto.DividendInfoResponse.DividendResponse;
import com.meridian.backend.dto.DividendPaidMessage;
import com.meridian.backend.exception.MarketDataUnavailableException;
import com.meridian.backend.exception.MarketDataUnreachableException;
import com.meridian.backend.exception.TickerNotFoundException;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.model.Dividend;
import com.meridian.backend.model.DividendPayment;
import com.meridian.backend.model.Holding;
import com.meridian.backend.model.Order;
import com.meridian.backend.model.OrderStatus;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.Portfolio;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.Transaction;
import com.meridian.backend.model.TransactionType;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.DividendPaymentRepository;
import com.meridian.backend.repository.DividendRepository;
import com.meridian.backend.repository.HoldingRepository;
import com.meridian.backend.repository.OrderRepository;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.repository.TransactionRepository;
import com.meridian.backend.websocket.PriceWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Cash dividends on stocks. Whoever held shares when a dividend's ex-date began (midnight New York time) is paid
 * shares x amount in USD cash on its payment date, as at a real broker. Dividend data comes from the market data
 * provider: held stocks are looked up on a schedule, and any stock when someone opens it, never more often than
 * marketdata.dividend-refresh-hours since requests come out of the same allowance as prices.
 */
@Service
public class DividendService {

    private static final Logger log = LoggerFactory.getLogger(DividendService.class);
    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final int SHOWN_DIVIDENDS = 8;

    private final MarketDataProvider provider;
    private final MarketDataProperties properties;
    private final TickerRepository tickerRepository;
    private final DividendRepository dividendRepository;
    private final DividendPaymentRepository paymentRepository;
    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final PriceWebSocketHandler priceWebSocketHandler;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DividendService(MarketDataProvider provider, MarketDataProperties properties, TickerRepository tickerRepository,
                           DividendRepository dividendRepository, DividendPaymentRepository paymentRepository,
                           HoldingRepository holdingRepository, OrderRepository orderRepository,
                           PortfolioRepository portfolioRepository, TransactionRepository transactionRepository,
                           PriceWebSocketHandler priceWebSocketHandler, ObjectMapper objectMapper,
                           PlatformTransactionManager transactionManager, Clock clock) {
        this.provider = provider;
        this.properties = properties;
        this.tickerRepository = tickerRepository;
        this.dividendRepository = dividendRepository;
        this.paymentRepository = paymentRepository;
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.priceWebSocketHandler = priceWebSocketHandler;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(NEW_YORK));
    }

    // ---- what a stock pays ----

    /** Has this stock's dividend data not been looked up recently enough? Never true for crypto. */
    public boolean isRefreshDue(Ticker ticker) {
        if (ticker.getAssetType() == AssetType.CRYPTO) return false;
        Instant checked = ticker.getDividendsCheckedAt();
        return checked == null || !clock.instant().isBefore(checked.plus(properties.getDividendRefreshInterval()));
    }

    /**
     * Asks the provider for the stock's dividends and stores those with an ex-date in the last year or later.
     * Already-paid dividends are left as they are. Synchronized so two lookups of one stock can't insert it twice.
     */
    public synchronized void refresh(Long tickerId) {
        Ticker ticker = tickerRepository.findById(tickerId).orElseThrow();
        List<DividendEvent> events = provider.fetchDividends(ticker.getSymbol());
        LocalDate oldest = today().minusYears(1);
        tx.executeWithoutResult(status -> {
            for (DividendEvent e : events) {
                if (e.exDate().isBefore(oldest)) continue;
                LocalDate payDate = e.payDate().isBefore(e.exDate()) ? e.exDate() : e.payDate();
                Optional<Dividend> known = dividendRepository.findByTickerIdAndExDate(tickerId, e.exDate());
                if (known.isEmpty()) {
                    dividendRepository.save(new Dividend(ticker, e.exDate(), payDate, e.amount()));
                } else if (known.get().getPaidOutAt() == null) {
                    known.get().setAmount(e.amount());
                    known.get().setPayDate(payDate);
                    dividendRepository.save(known.get());
                }
            }
            Ticker fresh = tickerRepository.findById(tickerId).orElseThrow();
            fresh.setDividendsCheckedAt(clock.instant());
            tickerRepository.save(fresh);
        });
    }

    /**
     * Looks up the dividends of up to {@code max} held stocks that are due, oldest lookup first.
     * Stops early when the provider can't be asked. Returns how many were looked up.
     */
    public int refreshHeldStocks(int max) {
        List<Ticker> due = tickerRepository.findAllById(holdingRepository.findHeldTickerIds()).stream()
                .filter(this::isRefreshDue)
                .sorted((a, b) -> a.getDividendsCheckedAt() == null ? -1 : b.getDividendsCheckedAt() == null ? 1
                        : a.getDividendsCheckedAt().compareTo(b.getDividendsCheckedAt()))
                .limit(max)
                .toList();
        int done = 0;
        for (Ticker ticker : due) {
            try {
                refresh(ticker.getId());
                done++;
            } catch (MarketDataUnavailableException | MarketDataUnreachableException e) {
                log.info("Dividend lookup paused: {}", e.getMessage());
                break;
            }
        }
        return done;
    }

    // ---- paying holders ----

    /** Pays every dividend whose payment date has come. Returns how many payments were made. */
    public int payDue() {
        int paid = 0;
        for (Dividend dividend : dividendRepository.findByPaidOutAtIsNullAndPayDateLessThanEqualOrderByPayDateAsc(today())) {
            paid += payOut(dividend.getId());
        }
        return paid;
    }

    // Each holder is paid in their own transaction under their portfolio lock; the unique (portfolio, dividend)
    // row makes a rerun after a crash skip whoever was already paid. The dividend is marked paid out last.
    private int payOut(Long dividendId) {
        Dividend dividend = tx.execute(s -> {
            Dividend d = dividendRepository.findById(dividendId).orElseThrow();
            d.getTicker().getSymbol(); // load it inside the session
            return d;
        });
        Long tickerId = dividend.getTicker().getId();
        Instant exStart = dividend.getExDate().atStartOfDay(NEW_YORK).toInstant();

        // Anyone holding now, plus anyone who sold since the ex-date began.
        TreeSet<Long> candidates = new TreeSet<>();
        tx.executeWithoutResult(s -> {
            holdingRepository.findByTickerId(tickerId).forEach(h -> candidates.add(h.getPortfolio().getId()));
            orderRepository.findByTickerIdAndStatusAndExecutedAtGreaterThanEqual(tickerId, OrderStatus.FILLED, exStart)
                    .forEach(o -> candidates.add(o.getPortfolio().getId()));
        });

        int paid = 0;
        for (Long portfolioId : candidates) {
            PaidNotice notice = tx.execute(s -> payOne(dividendId, portfolioId, exStart));
            if (notice != null) {
                paid++;
                notify(notice);
            }
        }
        tx.executeWithoutResult(s -> {
            Dividend d = dividendRepository.findById(dividendId).orElseThrow();
            d.setPaidOutAt(clock.instant());
            dividendRepository.save(d);
        });
        log.info("Paid dividend {} of {} ({} on {}) to {} portfolios", dividendId, dividend.getTicker().getSymbol(),
                dividend.getAmount(), dividend.getPayDate(), paid);
        return paid;
    }

    private record PaidNotice(Long userId, DividendPaidMessage message) {
    }

    private PaidNotice payOne(Long dividendId, Long portfolioId, Instant exStart) {
        Portfolio portfolio = portfolioRepository.findByIdForUpdate(portfolioId).orElse(null);
        if (portfolio == null || paymentRepository.existsByPortfolioIdAndDividendId(portfolioId, dividendId)) return null;
        Dividend dividend = dividendRepository.findById(dividendId).orElseThrow();
        Long tickerId = dividend.getTicker().getId();

        // Shares bought from the ex-date on don't qualify. Holding history before the first recorded buy is
        // unknown (very old accounts), so those pay nothing rather than guess.
        Optional<Order> firstBuy = orderRepository.findFirstByPortfolioIdAndTickerIdAndStatusAndTypeOrderByExecutedAtAsc(
                portfolioId, tickerId, OrderStatus.FILLED, OrderType.BUY);
        if (firstBuy.isEmpty() || firstBuy.get().getExecutedAt() == null || !firstBuy.get().getExecutedAt().isBefore(exStart)) {
            return null;
        }
        BigDecimal shares = holdingRepository.findByPortfolioIdAndTickerId(portfolioId, tickerId)
                .map(Holding::getQuantity).orElse(BigDecimal.ZERO);
        for (Order o : orderRepository.findByTickerIdAndStatusAndExecutedAtGreaterThanEqual(tickerId, OrderStatus.FILLED, exStart)) {
            if (!o.getPortfolio().getId().equals(portfolioId)) continue;
            shares = o.getType() == OrderType.BUY ? shares.subtract(o.getQuantity()) : shares.add(o.getQuantity());
        }
        if (shares.signum() <= 0) return null;
        BigDecimal amount = shares.multiply(dividend.getAmount()).setScale(2, RoundingMode.DOWN); // to the cent, as brokers pay
        if (amount.signum() <= 0) return null;

        portfolio.setCashBalance(portfolio.getCashBalance().add(amount));
        portfolioRepository.save(portfolio);
        String symbol = dividend.getTicker().getSymbol();
        String perShare = dividend.getAmount().stripTrailingZeros().toPlainString();
        transactionRepository.save(new Transaction(portfolio, TransactionType.DIVIDEND, amount, portfolio.getCashBalance(),
                "USD", "Dividend from " + symbol + ": $" + perShare + " × " + shares.stripTrailingZeros().toPlainString()
                + " shares", null));
        paymentRepository.save(new DividendPayment(portfolio, dividend, shares, amount, clock.instant()));
        return new PaidNotice(portfolio.getUser().getId(), new DividendPaidMessage(
                "DIVIDEND_PAID", symbol, shares, dividend.getAmount(), amount, dividend.getPayDate()));
    }

    private void notify(PaidNotice notice) {
        try {
            priceWebSocketHandler.broadcastToUser(notice.userId(), objectMapper.writeValueAsString(notice.message()));
        } catch (Exception e) {
            log.warn("Failed to send dividend notification to user {}", notice.userId(), e);
        }
    }

    // ---- what the stock page shows ----

    /** Looks the stock's dividends up first if they are due (a failed lookup just shows what is stored). */
    public DividendInfoResponse getInfo(User user, String symbol) {
        Ticker ticker = tickerRepository.findBySymbol(symbol.trim().toUpperCase())
                .orElseThrow(() -> new TickerNotFoundException(symbol));
        if (isRefreshDue(ticker)) {
            try {
                refresh(ticker.getId());
            } catch (MarketDataUnavailableException | MarketDataUnreachableException e) {
                log.info("Showing stored dividends for {}: {}", ticker.getSymbol(), e.getMessage());
            }
        }
        List<Dividend> all = dividendRepository.findByTickerIdOrderByExDateDesc(ticker.getId());
        LocalDate yearAgo = today().minusYears(1);
        BigDecimal trailingYear = all.stream()
                .filter(d -> d.getExDate().isAfter(yearAgo) && !d.getExDate().isAfter(today()))
                .map(Dividend::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal received = portfolioRepository.findByUserId(user.getId())
                .map(p -> paymentRepository.totalForPortfolioAndTicker(p.getId(), ticker.getId()))
                .orElse(BigDecimal.ZERO);
        List<DividendResponse> shown = all.stream().limit(SHOWN_DIVIDENDS)
                .map(d -> new DividendResponse(d.getExDate(), d.getPayDate(), d.getAmount())).toList();
        return new DividendInfoResponse(ticker.getSymbol(), shown, trailingYear, received.setScale(2, RoundingMode.HALF_UP));
    }
}
