package com.meridian.backend.retention;

import com.meridian.backend.repository.HistoryRow;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.PortfolioSnapshotRepository;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.repository.TickerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Keeps the two tables that grow by themselves from growing forever: prices (a row per ticker every poll) and
 * portfolio snapshots (a row per user every minute). The recent past keeps every row; older history is thinned
 * to hourly and then daily values (see {@link HistoryThinner}). Charts ask the server for a bounded number of
 * points anyway, so nothing a user can see changes.
 */
@Component
public class HistoryRetention {

    public record Result(int priceRows, int snapshotRows) {
    }

    private static final Logger log = LoggerFactory.getLogger(HistoryRetention.class);
    private static final int PAGE_SIZE = 5000;
    private static final int DELETE_BATCH = 1000;

    private final PriceHistoryRepository prices;
    private final PortfolioSnapshotRepository snapshots;
    private final TickerRepository tickers;
    private final PortfolioRepository portfolios;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration fullResolution;
    private final Duration dailyAfter;

    public HistoryRetention(PriceHistoryRepository prices, PortfolioSnapshotRepository snapshots,
                            TickerRepository tickers, PortfolioRepository portfolios,
                            PlatformTransactionManager transactionManager, Clock clock,
                            @Value("${history.retention.full-resolution-days:7}") long fullResolutionDays,
                            @Value("${history.retention.daily-after-days:90}") long dailyAfterDays) {
        this.prices = prices;
        this.snapshots = snapshots;
        this.tickers = tickers;
        this.portfolios = portfolios;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.fullResolution = Duration.ofDays(fullResolutionDays);
        this.dailyAfter = Duration.ofDays(Math.max(dailyAfterDays, fullResolutionDays));
    }

    public Result run() {
        int priceRows = thin("price history", tickers.findAllIds(),
                (group, afterAt, afterId, upTo, limit) -> toRows(prices.thinningPage(group, afterAt, afterId, upTo, PageRequest.ofSize(limit))),
                ids -> tx.executeWithoutResult(s -> prices.deleteAllByIdInBatch(ids)));
        int snapshotRows = thin("portfolio snapshots", portfolios.findAllIds(),
                (group, afterAt, afterId, upTo, limit) -> toRows(snapshots.thinningPage(group, afterAt, afterId, upTo, PageRequest.ofSize(limit))),
                ids -> tx.executeWithoutResult(s -> snapshots.deleteAllByIdInBatch(ids)));
        return new Result(priceRows, snapshotRows);
    }

    private int thin(String what, List<Long> groups, HistoryThinner.PageSource source, Consumer<List<Long>> sink) {
        try {
            int removed = HistoryThinner.thin(groups, source, sink, clock.instant(), fullResolution, dailyAfter, PAGE_SIZE, DELETE_BATCH);
            log.info("History retention: removed {} old {} rows", removed, what);
            return removed;
        } catch (RuntimeException e) {
            log.warn("History retention failed for {}", what, e); // a failed clean-up must never hurt the app
            return 0;
        }
    }

    private static List<HistoryThinner.Row> toRows(List<HistoryRow> rows) {
        return rows.stream().map(r -> new HistoryThinner.Row(r.getId(), r.getRecordedAt())).toList();
    }
}
