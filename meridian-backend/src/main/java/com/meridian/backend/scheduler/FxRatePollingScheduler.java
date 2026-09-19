package com.meridian.backend.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.service.FxRateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Only two pairs to poll (EUR/USD, GBP/USD), so unlike ticker polling this
// doesn't need to round-robin — both are refreshed every tick, well within
// Alpha Vantage's free-tier rate limit alongside the ticker scheduler.
@ConditionalOnProperty(prefix = "meridian.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@Component
public class FxRatePollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(FxRatePollingScheduler.class);

    private final FxRateService fxRateService;

    public FxRatePollingScheduler(FxRateService fxRateService) {
        this.fxRateService = fxRateService;
    }

    @Scheduled(fixedRate = 60000, initialDelay = 5000)
    public void pollRates() {
        for (SupportedCurrency currency : SupportedCurrency.values()) {
            if (currency == SupportedCurrency.USD) continue;
            try {
                fxRateService.pollAndStore(currency);
            } catch (Exception e) {
                log.warn("Scheduled FX poll failed for {}/USD", currency, e);
            }
        }
    }
}
