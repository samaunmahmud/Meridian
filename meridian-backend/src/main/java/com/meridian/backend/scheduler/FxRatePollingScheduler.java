package com.meridian.backend.scheduler;

import com.meridian.backend.exception.MarketDataUnavailableException;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.service.FxRateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Refreshes the EUR/USD and GBP/USD rates. Rates move slowly and every
// refresh costs provider requests, so the interval comes from
// marketdata.fx-poll-interval-ms (default: every 6 hours on Alpha Vantage's
// small free allowance, every 5 minutes on Finnhub).
@Component
@ConditionalOnProperty(prefix = "meridian.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FxRatePollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(FxRatePollingScheduler.class);

    private final FxRateService fxRateService;

    public FxRatePollingScheduler(FxRateService fxRateService) {
        this.fxRateService = fxRateService;
    }

    @Scheduled(fixedRateString = "#{@marketDataProperties.fxPollIntervalMs}", initialDelay = 5000)
    public void pollRates() {
        for (SupportedCurrency currency : SupportedCurrency.values()) {
            if (currency == SupportedCurrency.USD) continue;
            try {
                fxRateService.pollAndStore(currency);
            } catch (MarketDataUnavailableException e) {
                log.info("FX rate refresh skipped: {}", e.getMessage());
                return; // no point asking for the next currency either
            } catch (Exception e) {
                log.warn("Scheduled FX poll failed for {}/USD", currency, e);
            }
        }
    }
}
