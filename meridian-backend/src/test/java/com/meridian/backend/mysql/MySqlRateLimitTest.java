package com.meridian.backend.mysql;

import com.meridian.backend.security.RateLimitStore;
import com.meridian.backend.security.SlidingWindowLimiter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The database-backed rate limiter on a real MySQL server: its separate
// (REQUIRES_NEW) transaction and the "which event must expire" paging query
// behave the same as on H2. See MySqlTestDatabase for how to run it.
@EnabledIfEnvironmentVariable(named = MySqlTestDatabase.URL_ENV, matches = ".+")
class MySqlRateLimitTest {

    @BeforeAll
    static void onlyAgainstAThrowawayDatabase() {
        MySqlTestDatabase.assertSafeToWipe();
    }

    @BeforeEach
    void startFromAnEmptyDatabase() {
        MySqlTestDatabase.wipe();
    }

    @Test
    void attemptsStayCountedThroughARollbackAndTheWaitIsCalculatedCorrectly() {
        try (ConfigurableApplicationContext app = MySqlTestDatabase.boot()) {
            SlidingWindowLimiter limiter = new SlidingWindowLimiter(
                    app.getBean(RateLimitStore.class), "mysql-test", 3, Duration.ofMinutes(10));
            TransactionTemplate outer = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));

            // 5 events against a limit of 3, recorded inside a transaction that then fails.
            assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
                for (int i = 0; i < 5; i++) limiter.record("someone@example.com");
                throw new IllegalStateException("request failed after being counted");
            })).isInstanceOf(IllegalStateException.class);

            // Still counted, and blocked until the 3rd-oldest event leaves the 10-minute window.
            assertThat(limiter.retryAfterSeconds("someone@example.com")).isBetween(590L, 601L);
            assertThat(limiter.retryAfterSeconds("someone.else@example.com")).isZero();

            limiter.reset("someone@example.com");
            assertThat(limiter.retryAfterSeconds("someone@example.com")).isZero();
        }
    }
}
