package com.meridian.backend.service;

import com.meridian.backend.dto.BenchmarkResponse;
import com.meridian.backend.dto.BenchmarkResponse.Point;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.exception.TickerNotFoundException;
import com.meridian.backend.model.Order;
import com.meridian.backend.model.OrderStatus;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.PortfolioSnapshot;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.Transaction;
import com.meridian.backend.model.TransactionType;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.OrderRepository;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.PortfolioSnapshotRepository;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.repository.TransactionRepository;
import com.meridian.backend.service.TimeWeightedReturn.Flow;
import com.meridian.backend.service.TimeWeightedReturn.Valuation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** How the portfolio has done next to one ticker (an index fund, a stock, bitcoin) over the same window. */
@Service
public class BenchmarkService {

    public static final int DEFAULT_POINTS = 300;

    private final TickerRepository tickerRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final Clock clock;

    public BenchmarkService(TickerRepository tickerRepository, PortfolioRepository portfolioRepository,
                            PortfolioSnapshotRepository snapshotRepository, TransactionRepository transactionRepository,
                            OrderRepository orderRepository, PriceHistoryRepository priceHistoryRepository, Clock clock) {
        this.tickerRepository = tickerRepository;
        this.portfolioRepository = portfolioRepository;
        this.snapshotRepository = snapshotRepository;
        this.transactionRepository = transactionRepository;
        this.orderRepository = orderRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.clock = clock;
    }

    /**
     * @param range  "1D", "1W", "1M", "3M", "1Y" or "ALL" (also when null)
     * @param points at most this many points, spread evenly over the window; null means {@value #DEFAULT_POINTS}
     */
    @Transactional(readOnly = true)
    public BenchmarkResponse compare(User user, String symbol, String range, Integer points) {
        if (symbol == null || symbol.isBlank()) {
            throw new InvalidRequestException("symbol is required");
        }
        Ticker ticker = tickerRepository.findBySymbol(symbol.trim().toUpperCase())
                .orElseThrow(() -> new TickerNotFoundException(symbol));
        int maxPoints = points == null ? DEFAULT_POINTS : points;
        if (maxPoints < 2 || maxPoints > 5000) {
            throw new InvalidRequestException("points must be between 2 and 5000");
        }
        Duration window = MarketDataService.windowFor(range);

        Long portfolioId = portfolioRepository.findByUserId(user.getId()).map(p -> p.getId()).orElse(null);
        List<PortfolioSnapshot> snapshots = portfolioId == null ? List.of()
                : snapshotRepository.findByPortfolioIdOrderByRecordedAtAsc(portfolioId);

        // Growth is chained over the whole history and then rebased to the window, so a window that starts
        // between two snapshots still treats money moved before it correctly.
        List<Valuation> valuations = snapshots.stream().map(s -> new Valuation(s.getRecordedAt(), s.getTotalValue())).toList();
        double[] growth = TimeWeightedReturn.growth(valuations, portfolioId == null ? List.of() : flowsInUsd(portfolioId));

        Instant since = window == null ? null : clock.instant().minus(window);
        int start = 0;
        while (since != null && start < snapshots.size() && snapshots.get(start).getRecordedAt().isBefore(since)) start++;
        if (snapshots.size() - start < 2) {
            return new BenchmarkResponse(ticker.getSymbol(), ticker.getName(), null, null, List.of());
        }

        List<PriceHistory> prices = pricesFrom(ticker.getId(), snapshots.get(start).getRecordedAt());
        List<Point> all = new ArrayList<>(snapshots.size() - start);
        BigDecimal basePrice = null;
        int p = -1;
        for (int i = start; i < snapshots.size(); i++) {
            Instant at = snapshots.get(i).getRecordedAt();
            while (p + 1 < prices.size() && !prices.get(p + 1).getRecordedAt().isAfter(at)) p++;
            BigDecimal benchmark = null;
            if (p >= 0) {
                BigDecimal price = prices.get(p).getPrice();
                if (basePrice == null) basePrice = price;
                benchmark = percent(price.doubleValue() / basePrice.doubleValue());
            }
            all.add(new Point(at, percent(growth[i] / growth[start]), benchmark));
        }

        Point last = all.get(all.size() - 1);
        return new BenchmarkResponse(ticker.getSymbol(), ticker.getName(), last.portfolio(), last.benchmark(),
                Downsample.evenly(all, maxPoints));
    }

    // What moved money into or out of the USD total that snapshots record (USD cash plus holdings): USD deposits,
    // withdrawals and conversions, and trades settled in another currency (the shares arrive or leave without USD).
    private List<Flow> flowsInUsd(Long portfolioId) {
        List<Flow> flows = new ArrayList<>();
        for (Transaction t : transactionRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioId)) {
            boolean usd = t.getCurrency() == null || t.getCurrency().equals(SupportedCurrency.USD.name());
            if (!usd) continue;
            if (t.getType() == TransactionType.DEPOSIT) flows.add(new Flow(t.getCreatedAt(), t.getAmount().abs()));
            else if (t.getType() == TransactionType.WITHDRAWAL) flows.add(new Flow(t.getCreatedAt(), t.getAmount().abs().negate()));
            else if (t.getType() == TransactionType.CONVERSION) flows.add(new Flow(t.getCreatedAt(), t.getAmount()));
        }
        for (Order o : orderRepository.findByPortfolioIdAndStatusOrderByCreatedAtDesc(portfolioId, OrderStatus.FILLED)) {
            SupportedCurrency settled = o.getSettlementCurrency();
            if (settled == null || settled == SupportedCurrency.USD || o.getPrice() == null || o.getExecutedAt() == null) continue;
            BigDecimal shares = o.getPrice().multiply(o.getQuantity());
            flows.add(new Flow(o.getExecutedAt(), o.getType() == OrderType.BUY ? shares : shares.negate()));
        }
        return flows;
    }

    // Oldest first: the last price at or before `from` (what it was worth when the window opened), then every later one.
    private List<PriceHistory> pricesFrom(Long tickerId, Instant from) {
        List<PriceHistory> newestFirst = priceHistoryRepository
                .findByTickerIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(tickerId, from);
        List<PriceHistory> prices = new ArrayList<>(newestFirst.size() + 1);
        priceHistoryRepository.findFirstByTickerIdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(tickerId, from)
                .filter(before -> before.getRecordedAt().isBefore(from))
                .ifPresent(prices::add);
        for (int i = newestFirst.size() - 1; i >= 0; i--) prices.add(newestFirst.get(i));
        return prices;
    }

    private static BigDecimal percent(double growth) {
        return BigDecimal.valueOf((growth - 1) * 100).setScale(2, RoundingMode.HALF_UP);
    }
}
