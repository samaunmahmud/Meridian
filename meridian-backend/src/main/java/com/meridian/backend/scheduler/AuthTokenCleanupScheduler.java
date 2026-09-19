package com.meridian.backend.scheduler;

import com.meridian.backend.repository.AuthTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

// Deletes email links that expired more than a week ago.
@ConditionalOnProperty(prefix = "meridian.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@Component
public class AuthTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenCleanupScheduler.class);

    private final AuthTokenRepository tokens;
    private final Clock clock;

    public AuthTokenCleanupScheduler(AuthTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 86_400_000, initialDelay = 120_000)
    @Transactional
    public void purgeExpiredTokens() {
        try {
            int removed = tokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(7)));
            if (removed > 0) log.info("Removed {} expired email links", removed);
        } catch (Exception e) {
            log.warn("Email-link cleanup failed", e);
        }
    }
}
