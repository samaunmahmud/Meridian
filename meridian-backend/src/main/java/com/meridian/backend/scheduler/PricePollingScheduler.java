package com.meridian.backend.scheduler;

import com.meridian.backend.model.Ticker;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.service.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Polls whatever tickers actually exist in the database — no longer a fixed
// list. Adding a new ticker via the "add stock" feature means it joins this
// rotation automatically on the very next tick. One ticker is polled every
// 20 seconds, so with N tickers tracked, each one refreshes roughly every
// N*20 seconds — this naturally respects Alpha Vantage's free-tier rate
// limit (5 calls/minute) no matter how many tickers get added.
@Component
public class PricePollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PricePollingScheduler.class);

    private int index = 0;

    private final MarketDataService marketDataService;
    private final TickerRepository tickerRepository;

    public PricePollingScheduler(MarketDataService marketDataService, TickerRepository tickerRepository) {
        this.marketDataService = marketDataService;
        this.tickerRepository = tickerRepository;
    }

    @Scheduled(fixedRate = 20000)
    public void pollNextTicker() {
        List<Ticker> tickers = tickerRepository.findAll();
        if (tickers.isEmpty()) {
            return; // nothing tracked yet — nothing to poll
        }

        if (index >= tickers.size()) {
            index = 0; // list shrank or grew since last tick — stay safe
        }

        Ticker ticker = tickers.get(index);
        index = (index + 1) % tickers.size();

        try {
            marketDataService.pollAndStore(ticker.getSymbol(), ticker.getName(), ticker.getExchange(), ticker.getAssetType());
        } catch (Exception e) {
            log.warn("Scheduled poll failed for {}", ticker.getSymbol(), e);
        }
    }
}
