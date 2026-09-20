package com.meridian.backend.scheduler;

import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.exception.MarketDataUnavailableException;
import com.meridian.backend.exception.MarketDataUnreachableException;
import com.meridian.backend.market.MarketCalendar;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.service.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

// Polls whatever tickers actually exist in the database, one per poll, in
// rotation — adding a ticker means it joins the rotation automatically.
//
// A stock is not polled while its market is closed once its closing price is recorded: the provider would
// only repeat that price, and every such request comes out of the daily allowance. Crypto is polled around
// the clock. When nothing needs a poll the tick does nothing and costs no request.
//
// The tick itself is cheap (every 20 s) but a poll only happens when at
// least marketdata.getTickerPollSpacingMs() has passed since the last one.
// That spacing is derived from the daily request budget, so the allowance
// is spread across the whole day: with a 25-request free plan prices refresh
// about every 1.5 hours all day, instead of every 20 seconds until the
// allowance is gone in the first few minutes (and then nothing until tomorrow).
@Component
@ConditionalOnProperty(prefix = "meridian.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PricePollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PricePollingScheduler.class);

    private int index = 0;
    private Instant lastPollAt;

    private final MarketDataService marketDataService;
    private final TickerRepository tickerRepository;
    private final MarketDataProperties properties;
    private final Clock clock;
    private final MarketCalendar marketCalendar;
    private final PriceHistoryRepository priceHistoryRepository;

    public PricePollingScheduler(MarketDataService marketDataService,
                                 TickerRepository tickerRepository,
                                 MarketDataProperties properties,
                                 Clock clock,
                                 MarketCalendar marketCalendar,
                                 PriceHistoryRepository priceHistoryRepository) {
        this.marketDataService = marketDataService;
        this.tickerRepository = tickerRepository;
        this.properties = properties;
        this.clock = clock;
        this.marketCalendar = marketCalendar;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Scheduled(fixedRate = 20000)
    public void pollNextTicker() {
        Instant now = clock.instant();
        if (lastPollAt != null && now.toEpochMilli() - lastPollAt.toEpochMilli() < properties.getTickerPollSpacingMs()) {
            return; // not due yet — keeps us inside the provider's allowance
        }

        List<Ticker> tickers = tickerRepository.findAll();
        if (tickers.isEmpty()) {
            return; // nothing tracked yet — nothing to poll
        }

        if (index >= tickers.size()) {
            index = 0; // list shrank or grew since last tick — stay safe
        }

        // The next ticker in the rotation that has a reason to be polled.
        int chosen = -1;
        for (int i = 0; i < tickers.size(); i++) {
            int candidate = (index + i) % tickers.size();
            if (needsPoll(tickers.get(candidate), now)) {
                chosen = candidate;
                break;
            }
        }
        if (chosen < 0) {
            return; // every stock market is closed and every price is already the close: nothing to ask for
        }
        index = chosen;
        Ticker ticker = tickers.get(index);

        try {
            marketDataService.pollAndStore(ticker.getSymbol(), ticker.getName(), ticker.getExchange(), ticker.getAssetType());
        } catch (MarketDataUnavailableException e) {
            // Allowance used up / provider limit: stay on this ticker and try
            // again on a later tick without making any request.
            log.info("Price poll skipped: {}", e.getMessage());
            return;
        } catch (MarketDataUnreachableException e) {
            // The provider is down or answering nonsense. One line, not a stack trace on every
            // poll; the spacing below still applies, so an outage does not use up the budget.
            log.warn("Price poll for {} failed: {} ({})", ticker.getSymbol(), e.getMessage(), rootCause(e));
        } catch (Exception e) {
            log.warn("Scheduled poll failed for {}", ticker.getSymbol(), e);
        }

        lastPollAt = now;
        index = (index + 1) % tickers.size();
    }

    // Crypto: always. A stock: while its market is open, and once after the close so that the closing price
    // is on record (a price recorded before the last close is not the close yet).
    private boolean needsPoll(Ticker ticker, Instant now) {
        if (ticker.getAssetType() == AssetType.CRYPTO || marketCalendar.status(AssetType.STOCK, now).open()) {
            return true;
        }
        Instant lastClose = marketCalendar.lastStockClose(now);
        if (lastClose == null) {
            return true; // trading hours are not enforced
        }
        return priceHistoryRepository.findFirstByTickerIdOrderByRecordedAtDesc(ticker.getId())
                .map(PriceHistory::getRecordedAt)
                .map(recordedAt -> recordedAt.isBefore(lastClose))
                .orElse(true);
    }

    private static String rootCause(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
