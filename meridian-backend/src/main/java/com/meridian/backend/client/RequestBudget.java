package com.meridian.backend.client;

import com.meridian.backend.config.MarketDataProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

// Counts every request we make to the price provider so we never exceed
// its allowance. Free plans allow very few calls per day, and the provider
// answers 200 OK with an error message once you go over — which used to be
// mistaken for "no price for this symbol". Two mechanisms:
//   1. a daily cap of dailyRequestBudget calls (resets at 00:00 UTC), and
//   2. a temporary block when the provider itself says "rate limit reached".
@Component
public class RequestBudget {

    private static final long MINUTE_BLOCK_SECONDS = 65;

    private final MarketDataProperties properties;
    private final Clock clock;

    private LocalDate day;
    private int used;
    private Instant blockedUntil = Instant.EPOCH;

    public RequestBudget(MarketDataProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** True if a request may be made now (and counts it). */
    public synchronized boolean tryAcquire() {
        rollDay();
        if (clock.instant().isBefore(blockedUntil)) return false;
        if (used >= properties.getDailyRequestBudget()) return false;
        used++;
        return true;
    }

    /**
     * The provider told us we're over its limit. A "per day" limit blocks us
     * until midnight UTC; anything else (per-minute limits) for about a minute.
     */
    public synchronized void blockFor(String providerMessage) {
        String text = providerMessage == null ? "" : providerMessage.toLowerCase();
        boolean dailyLimit = (text.contains("per day") || text.contains("daily")) && !text.contains("per minute");
        Instant until = dailyLimit
                ? LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                : clock.instant().plusSeconds(MINUTE_BLOCK_SECONDS);
        if (until.isAfter(blockedUntil)) {
            blockedUntil = until;
        }
    }

    public synchronized int remainingToday() {
        rollDay();
        return Math.max(0, properties.getDailyRequestBudget() - used);
    }

    private void rollDay() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!today.equals(day)) {
            day = today;
            used = 0;
        }
    }
}
