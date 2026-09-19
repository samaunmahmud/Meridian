package com.meridian.backend.marketdata;

import com.meridian.backend.MutableClock;
import com.meridian.backend.client.RequestBudget;
import com.meridian.backend.config.MarketDataProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RequestBudgetTest {

    private MarketDataProperties props(int dailyBudget) {
        MarketDataProperties p = new MarketDataProperties();
        p.setDailyRequestBudget(dailyBudget);
        return p;
    }

    @Test
    void allowsExactlyTheDailyBudgetThenRefuses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
        RequestBudget budget = new RequestBudget(props(3), clock);

        assertThat(budget.tryAcquire()).isTrue();
        assertThat(budget.tryAcquire()).isTrue();
        assertThat(budget.tryAcquire()).isTrue();
        assertThat(budget.tryAcquire()).isFalse();
        assertThat(budget.remainingToday()).isZero();
    }

    @Test
    void budgetResetsAtMidnightUtc() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-19T23:59:00Z"));
        RequestBudget budget = new RequestBudget(props(1), clock);
        assertThat(budget.tryAcquire()).isTrue();
        assertThat(budget.tryAcquire()).isFalse();

        clock.advance(Duration.ofMinutes(2)); // now 00:01 the next day

        assertThat(budget.tryAcquire()).isTrue();
    }

    @Test
    void aPerMinuteLimitMessageBlocksForAboutAMinute() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
        RequestBudget budget = new RequestBudget(props(100), clock);

        budget.blockFor("Our standard API call frequency is 5 calls per minute and 500 calls per day.");

        assertThat(budget.tryAcquire()).isFalse();
        clock.advance(Duration.ofSeconds(30));
        assertThat(budget.tryAcquire()).isFalse();
        clock.advance(Duration.ofSeconds(40));
        assertThat(budget.tryAcquire()).isTrue();
    }

    @Test
    void aDailyLimitMessageBlocksUntilMidnightUtc() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
        RequestBudget budget = new RequestBudget(props(100), clock);

        budget.blockFor("Our standard API rate limit is 25 requests per day.");

        clock.advance(Duration.ofHours(13)); // 23:00 — still the same day
        assertThat(budget.tryAcquire()).isFalse();
        clock.advance(Duration.ofHours(2));  // 01:00 next day
        assertThat(budget.tryAcquire()).isTrue();
    }
}
