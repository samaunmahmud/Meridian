package com.meridian.backend.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Nightly, in the quiet hours (server time).
@Component
@ConditionalOnProperty(prefix = "meridian.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HistoryRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(HistoryRetentionScheduler.class);

    private final HistoryRetention retention;

    public HistoryRetentionScheduler(HistoryRetention retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "${history.retention.cron:0 30 3 * * *}")
    public void thinOldHistory() {
        try {
            HistoryRetention.Result result = retention.run();
            log.info("Nightly history retention done: {} price rows, {} snapshot rows removed", result.priceRows(), result.snapshotRows());
        } catch (RuntimeException e) {
            log.warn("Nightly history retention failed", e);
        }
    }
}
