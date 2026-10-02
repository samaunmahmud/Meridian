package com.meridian.backend.scheduler;

import com.meridian.backend.service.DividendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Once an hour: look up the dividends of a couple of held stocks that are due (a few requests a day at most,
// so prices keep nearly all of the allowance), then pay out any dividend whose payment date has come.
@ConditionalOnProperty(prefix = "meridian.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@Component
public class DividendScheduler {

    private static final Logger log = LoggerFactory.getLogger(DividendScheduler.class);
    static final int LOOKUPS_PER_RUN = 2;

    private final DividendService dividendService;

    public DividendScheduler(DividendService dividendService) {
        this.dividendService = dividendService;
    }

    @Scheduled(fixedRate = 3_600_000, initialDelay = 120_000)
    public void run() {
        try {
            dividendService.refreshHeldStocks(LOOKUPS_PER_RUN);
        } catch (Exception e) {
            log.warn("Scheduled dividend lookup failed", e);
        }
        try {
            dividendService.payDue();
        } catch (Exception e) {
            log.warn("Scheduled dividend payout failed", e);
        }
    }
}
