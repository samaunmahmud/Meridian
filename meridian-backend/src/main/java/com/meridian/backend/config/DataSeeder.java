package com.meridian.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.service.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// Runs once when the app starts. If no tickers exist yet (a brand new
// database), seed 3 defaults with an immediate poll each, so the app isn't
// completely empty on first run. If tickers already exist (normal restart),
// this does nothing — the dynamic scheduler just keeps going.
@ConditionalOnProperty(prefix = "meridian.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final TickerRepository tickerRepository;
    private final MarketDataService marketDataService;

    public DataSeeder(TickerRepository tickerRepository, MarketDataService marketDataService) {
        this.tickerRepository = tickerRepository;
        this.marketDataService = marketDataService;
    }

    @Override
    public void run(String... args) {
        if (tickerRepository.count() > 0) {
            return; // already have tickers — nothing to seed
        }

        log.info("No tickers found — seeding default watchlist");
        try {
            marketDataService.pollAndStore("IBM", "International Business Machines", "NYSE", AssetType.STOCK);
        } catch (Exception e) {
            log.warn("Failed to seed IBM", e);
        }
        try {
            marketDataService.pollAndStore("BTC", "Bitcoin", "CRYPTO", AssetType.CRYPTO);
        } catch (Exception e) {
            log.warn("Failed to seed BTC", e);
        }
    }
}
