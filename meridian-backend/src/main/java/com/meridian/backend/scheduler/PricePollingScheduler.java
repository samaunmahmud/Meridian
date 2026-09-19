package com.meridian.backend.scheduler;

import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.exception.MarketDataUnavailableException;
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

    public PricePollingScheduler(MarketDataService marketDataService,
                                 TickerRepository tickerRepository,
                                 MarketDataProperties properties,
                                 Clock clock) {
        this.marketDataService = marketDataService;
        this.tickerRepository = tickerRepository;
        this.properties = properties;
        this.clock = clock;
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

        Ticker ticker = tickers.get(index);

        try {
            marketDataService.pollAndStore(ticker.getSymbol(), ticker.getName(), ticker.getExchange(), ticker.getAssetType());
        } catch (MarketDataUnavailableException e) {
            // Allowance used up / provider limit: stay on this ticker and try
            // again on a later tick without making any request.
            log.info("Price poll skipped: {}", e.getMessage());
            return;
        } catch (Exception e) {
            log.warn("Scheduled poll failed for {}", ticker.getSymbol(), e);
        }

        lastPollAt = now;
        index = (index + 1) % tickers.size();
    }
}
