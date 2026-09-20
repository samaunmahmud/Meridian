package com.meridian.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Settings for where prices come from and how fast we may ask for them.
// Bound from application.properties / .env (see the marketdata.* keys).
@Component
@ConfigurationProperties(prefix = "marketdata")
public class MarketDataProperties {

    private static final long DAY_MS = 86_400_000L;
    // Number of non-USD currencies whose rate to USD is polled (EUR, GBP).
    private static final int FX_PAIRS = 2;

    // "alphavantage" (default) or "finnhub".
    private String provider = "alphavantage";

    // Requests per UTC day we allow ourselves. 0 or less = the provider's
    // typical free-tier allowance: Alpha Vantage 25/day, Finnhub 50,000/day.
    // Raise it if your key is on a paid plan.
    private int dailyRequestBudget = 0;

    // Never poll tickers more often than this, even when the budget would allow it.
    private long tickerPollIntervalMs = 20_000;

    // How often FX rates are refreshed. 0 or less = provider default
    // (Alpha Vantage 6 h, because every refresh costs requests; Finnhub 5 min).
    private long fxPollIntervalMs = 0;

    // Trades (market orders, recurring buys, currency conversions) refuse to run on a price or
    // exchange rate older than this: it means the feed is down, and an old price is not a
    // price anyone can trade at. 0 or less = worked out from how often data is refreshed.
    private long maxPriceAgeMinutes = 0;
    private long maxFxRateAgeMinutes = 0;

    // A ticker is refreshed once per rotation through all tracked tickers, so the normal
    // age of a price is up to (poll spacing x number of tickers). Allow three rotations
    // (two missed rounds) and never less than 15 minutes.
    public Duration getMaxPriceAge(long trackedTickers) {
        if (maxPriceAgeMinutes > 0) return Duration.ofMinutes(maxPriceAgeMinutes);
        long rotationMs = getTickerPollSpacingMs() * Math.max(1, trackedTickers);
        return Duration.ofMillis(Math.max(15 * 60_000L, 3 * rotationMs));
    }

    // Same idea for exchange rates: three refresh intervals, at least 30 minutes.
    public Duration getMaxFxRateAge() {
        if (maxFxRateAgeMinutes > 0) return Duration.ofMinutes(maxFxRateAgeMinutes);
        return Duration.ofMillis(Math.max(30 * 60_000L, 3 * getFxPollIntervalMs()));
    }

    public void setMaxPriceAgeMinutes(long maxPriceAgeMinutes) {
        this.maxPriceAgeMinutes = maxPriceAgeMinutes;
    }

    public void setMaxFxRateAgeMinutes(long maxFxRateAgeMinutes) {
        this.maxFxRateAgeMinutes = maxFxRateAgeMinutes;
    }

    public boolean isFinnhub() {
        return "finnhub".equalsIgnoreCase(provider);
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getDailyRequestBudget() {
        return dailyRequestBudget > 0 ? dailyRequestBudget : (isFinnhub() ? 50_000 : 25);
    }

    public void setDailyRequestBudget(int dailyRequestBudget) {
        this.dailyRequestBudget = dailyRequestBudget;
    }

    public long getFxPollIntervalMs() {
        return fxPollIntervalMs > 0 ? fxPollIntervalMs : (isFinnhub() ? 300_000L : 6 * 3_600_000L);
    }

    public void setFxPollIntervalMs(long fxPollIntervalMs) {
        this.fxPollIntervalMs = fxPollIntervalMs;
    }

    public void setTickerPollIntervalMs(long tickerPollIntervalMs) {
        this.tickerPollIntervalMs = tickerPollIntervalMs;
    }

    // Minimum gap between two ticker polls: the larger of the configured
    // interval and what spreads the ticker share of the daily budget evenly
    // over the whole day (so the budget isn't burnt in the first few minutes
    // after startup and then leaves prices frozen until midnight).
    public long getTickerPollSpacingMs() {
        long fxRequestsPerDay = (long) Math.ceil((double) DAY_MS / getFxPollIntervalMs()) * FX_PAIRS;
        long tickerBudget = Math.max(1, getDailyRequestBudget() - fxRequestsPerDay);
        return Math.max(tickerPollIntervalMs, DAY_MS / tickerBudget);
    }
}
