package com.meridian.backend.security;

import com.meridian.backend.IntegrationTestBase;
import com.meridian.backend.MutableClock;
import com.meridian.backend.repository.RateLimitEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The limiter counts in the database, so these run against the real Spring
// context (in-memory database) with a clock the test moves by hand.
class SlidingWindowLimiterTest extends IntegrationTestBase {

    @TestConfiguration
    static class FakeClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
        }
    }

    @Autowired MutableClock clock;
    @Autowired RateLimitStore store;
    @Autowired RateLimitEventRepository events;
    @Autowired PlatformTransactionManager transactions;

    private SlidingWindowLimiter limiter(String name) {
        return new SlidingWindowLimiter(store, name, 3, Duration.ofMinutes(10));
    }

    private static String key() {
        return "key-" + UUID.randomUUID();
    }

    @Test
    void allowsUpToTheLimitThenReportsHowLongToWait() {
        SlidingWindowLimiter limiter = limiter("t1");
        String a = key();

        limiter.record(a);
        clock.advance(Duration.ofMinutes(1));
        limiter.record(a);
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.retryAfterSeconds(a)).isZero(); // 2 of 3 used

        limiter.record(a);
        clock.advance(Duration.ofMinutes(1)); // the oldest event is 3 minutes old and expires 7 minutes from now

        assertThat(limiter.retryAfterSeconds(a)).isBetween(420L, 422L);
    }

    @Test
    void eventsExpireOutOfTheWindow() {
        SlidingWindowLimiter limiter = limiter("t2");
        String a = key();
        limiter.record(a);
        limiter.record(a);
        limiter.record(a);
        assertThat(limiter.retryAfterSeconds(a)).isPositive();

        clock.advance(Duration.ofMinutes(11));

        assertThat(limiter.retryAfterSeconds(a)).isZero();
    }

    @Test
    void keysAreIndependentAndCanBeReset() {
        SlidingWindowLimiter limiter = limiter("t3");
        String a = key();
        String b = key();
        for (int i = 0; i < 3; i++) limiter.record(a);

        assertThat(limiter.retryAfterSeconds(a)).isPositive();
        assertThat(limiter.retryAfterSeconds(b)).isZero();

        limiter.reset(a);
        assertThat(limiter.retryAfterSeconds(a)).isZero();
    }

    @Test
    void limitersWithDifferentNamesDoNotCountEachOthersEvents() {
        String shared = key();
        for (int i = 0; i < 3; i++) limiter("t4-login").record(shared);

        assertThat(limiter("t4-login").retryAfterSeconds(shared)).isPositive();
        assertThat(limiter("t4-signup").retryAfterSeconds(shared)).isZero();
    }

    @Test
    void theCountIsSharedByEveryServerInstance() {
        // Two limiter objects on the same store stand in for two servers.
        SlidingWindowLimiter serverA = limiter("t5");
        SlidingWindowLimiter serverB = limiter("t5");
        String a = key();

        serverA.record(a);
        serverB.record(a);
        serverA.record(a);

        assertThat(serverB.retryAfterSeconds(a)).isPositive();
    }

    @Test
    void whenOverTheLimitTheWaitIsUntilEnoughEventsHaveExpired() {
        SlidingWindowLimiter limiter = limiter("t6");
        String a = key();
        // 5 events, one minute apart, against a limit of 3: the count only
        // drops below 3 once the 3rd-oldest event (not the oldest) expires.
        for (int i = 0; i < 5; i++) {
            limiter.record(a);
            clock.advance(Duration.ofMinutes(1));
        }
        // now 10:05 relative; events at :00 :01 :02 :03 :04. Blocked until the event at :02 expires (at :12).
        assertThat(limiter.retryAfterSeconds(a)).isBetween(420L, 422L);
    }

    @Test
    void aFailedAttemptStaysCountedWhenTheSurroundingTransactionRollsBack() {
        SlidingWindowLimiter limiter = limiter("t7");
        String a = key();
        TransactionTemplate outer = new TransactionTemplate(transactions);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            for (int i = 0; i < 3; i++) limiter.record(a);
            throw new IllegalStateException("the request failed after being counted");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(limiter.retryAfterSeconds(a)).isPositive();
    }

    @Test
    void rawKeysAreNeverStored() {
        String email = "someone." + UUID.randomUUID() + "@example.com";
        limiter("t8").record(email);

        assertThat(events.findAll())
                .allSatisfy(e -> assertThat(e.getBucket()).hasSize(64).doesNotContain("someone"));
    }

    @Test
    void purgingRemovesOnlyEventsOlderThanTheAge() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(store, "t9", 1, Duration.ofDays(3));
        String old = key();
        String recent = key();
        limiter.record(old);
        clock.advance(Duration.ofDays(2));
        limiter.record(recent);

        assertThat(store.purgeOlderThan(Duration.ofDays(1))).isGreaterThanOrEqualTo(1);

        assertThat(limiter.retryAfterSeconds(old)).isZero();    // purged
        assertThat(limiter.retryAfterSeconds(recent)).isPositive(); // kept
    }
}
