package com.meridian.backend.scheduler;

import com.meridian.backend.security.RateLimitStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Each limiter already trims its own bucket whenever it records an event; this
// clears out buckets nobody touches again (a one-off IP, an email that was
// only mistyped once). The longest limiter window is 1 hour, so a day is safe.
@ConditionalOnProperty(prefix = "meridian.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@Component
public class RateLimitCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RateLimitCleanupScheduler.class);

    private final RateLimitStore store;

    public RateLimitCleanupScheduler(RateLimitStore store) {
        this.store = store;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void purgeStaleEvents() {
        try {
            int removed = store.purgeOlderThan(Duration.ofDays(1));
            if (removed > 0) log.info("Removed {} stale rate-limit events", removed);
        } catch (Exception e) {
            log.warn("Rate-limit cleanup failed", e);
        }
    }
}
