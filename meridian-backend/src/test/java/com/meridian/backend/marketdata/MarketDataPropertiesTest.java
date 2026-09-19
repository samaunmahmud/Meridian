package com.meridian.backend.marketdata;

import com.meridian.backend.config.MarketDataProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataPropertiesTest {

    @Test
    void alphaVantageDefaultsSpreadTheSmallFreeAllowanceOverTheDay() {
        MarketDataProperties p = new MarketDataProperties();

        assertThat(p.getDailyRequestBudget()).isEqualTo(25);
        assertThat(p.getFxPollIntervalMs()).isEqualTo(6 * 3_600_000L);
        // 25 requests - 8 for FX (2 pairs x 4 a day) = 17 for prices -> one every ~85 minutes
        assertThat(p.getTickerPollSpacingMs()).isBetween(80 * 60_000L, 90 * 60_000L);
    }

    @Test
    void finnhubDefaultsAllowFrequentPolling() {
        MarketDataProperties p = new MarketDataProperties();
        p.setProvider("finnhub");

        assertThat(p.getDailyRequestBudget()).isEqualTo(50_000);
        assertThat(p.getFxPollIntervalMs()).isEqualTo(300_000L);
        assertThat(p.getTickerPollSpacingMs()).isEqualTo(20_000L); // the configured floor
    }

    @Test
    void anExplicitBudgetOverridesTheProviderDefault() {
        MarketDataProperties p = new MarketDataProperties();
        p.setDailyRequestBudget(5000);

        assertThat(p.getDailyRequestBudget()).isEqualTo(5000);
    }
}
