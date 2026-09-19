package com.meridian.backend.marketdata;

import com.meridian.backend.client.AlphaVantageProvider;
import com.meridian.backend.client.FinnhubProvider;
import com.meridian.backend.client.MarketDataProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// The whole application context must start with either provider selected.
class ProviderSelectionTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class Default {
        @Autowired MarketDataProvider provider;

        @Test
        void usesAlphaVantageByDefault() {
            assertThat(provider).isInstanceOf(AlphaVantageProvider.class);
        }
    }

    @Nested
    @SpringBootTest(properties = {"marketdata.provider=finnhub", "FINNHUB_API_KEY=test-key"})
    @ActiveProfiles("test")
    class Finnhub {
        @Autowired MarketDataProvider provider;

        @Test
        void switchesToFinnhubWithOneSetting() {
            assertThat(provider).isInstanceOf(FinnhubProvider.class);
        }
    }
}
