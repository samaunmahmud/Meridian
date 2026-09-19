package com.meridian.backend.security;

import com.meridian.backend.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
    private final SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, Duration.ofMinutes(10), clock);

    @Test
    void allowsUpToTheLimitThenReportsHowLongToWait() {
        limiter.record("a");
        clock.advance(Duration.ofMinutes(1));
        limiter.record("a");
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.retryAfterSeconds("a")).isZero(); // 2 of 3 used

        limiter.record("a");
        clock.advance(Duration.ofMinutes(1)); // now 10:03; the oldest event (10:00) expires at 10:10

        assertThat(limiter.retryAfterSeconds("a")).isBetween(420L, 422L);
    }

    @Test
    void eventsExpireOutOfTheWindow() {
        limiter.record("a");
        limiter.record("a");
        limiter.record("a");
        assertThat(limiter.retryAfterSeconds("a")).isPositive();

        clock.advance(Duration.ofMinutes(11));

        assertThat(limiter.retryAfterSeconds("a")).isZero();
    }

    @Test
    void keysAreIndependentAndCanBeReset() {
        for (int i = 0; i < 3; i++) limiter.record("a");

        assertThat(limiter.retryAfterSeconds("a")).isPositive();
        assertThat(limiter.retryAfterSeconds("b")).isZero();

        limiter.reset("a");
        assertThat(limiter.retryAfterSeconds("a")).isZero();
    }
}
