package com.meridian.backend.marketdata;

import com.meridian.backend.client.AlphaVantageClient;
import com.meridian.backend.client.AlphaVantageProvider;
import com.meridian.backend.client.RequestBudget;
import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.dto.TickerSearchResult;
import com.meridian.backend.exception.MarketDataUnavailableException;
import com.meridian.backend.model.SupportedCurrency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AlphaVantageProviderTest {

    private MockRestServiceServer server;
    private AlphaVantageProvider provider;

    private void build(int dailyBudget) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MarketDataProperties props = new MarketDataProperties();
        props.setDailyRequestBudget(dailyBudget);
        RequestBudget budget = new RequestBudget(props, new MutableClock(Instant.parse("2026-09-19T10:00:00Z")));
        provider = new AlphaVantageProvider(new AlphaVantageClient(builder, "test-key"), budget);
    }

    @BeforeEach
    void setUp() {
        build(25);
    }

    @Test
    void parsesAStockQuote() {
        server.expect(requestTo(containsString("function=GLOBAL_QUOTE"))).andRespond(withSuccess(
                "{\"Global Quote\":{\"01. symbol\":\"IBM\",\"05. price\":\"268.1400\"}}", MediaType.APPLICATION_JSON));

        assertThat(provider.fetchStockPrice("IBM")).isEqualByComparingTo("268.14");
    }

    @Test
    void anUnknownSymbolIsNullNotAnError() {
        server.expect(requestTo(containsString("GLOBAL_QUOTE"))).andRespond(withSuccess("{\"Global Quote\":{}}", MediaType.APPLICATION_JSON));

        assertThat(provider.fetchStockPrice("NOPE")).isNull();
    }

    @Test
    void parsesAnFxRateAndACryptoPrice() {
        String body = "{\"Realtime Currency Exchange Rate\":{\"5. Exchange Rate\":\"1.09000000\"}}";
        server.expect(requestTo(containsString("from_currency=EUR"))).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("from_currency=BTC"))).andRespond(withSuccess(
                "{\"Realtime Currency Exchange Rate\":{\"5. Exchange Rate\":\"96420.50000000\"}}", MediaType.APPLICATION_JSON));

        assertThat(provider.fetchUsdRate(SupportedCurrency.EUR)).isEqualByComparingTo("1.09");
        assertThat(provider.fetchCryptoPrice("BTC")).isEqualByComparingTo("96420.50");
    }

    @Test
    void mapsSearchResults() {
        server.expect(requestTo(containsString("SYMBOL_SEARCH"))).andRespond(withSuccess(
                "{\"bestMatches\":[{\"1. symbol\":\"AAPL\",\"2. name\":\"Apple Inc\",\"4. region\":\"United States\"}]}",
                MediaType.APPLICATION_JSON));

        List<TickerSearchResult> results = provider.searchSymbols("apple");

        assertThat(results).containsExactly(new TickerSearchResult("AAPL", "Apple Inc", "United States"));
    }

    @Test
    void aRateLimitReplyIsRecognisedAndStopsFurtherRequests() {
        // The provider answers 200 OK with an "Information" message instead of data.
        server.expect(ExpectedCount.once(), requestTo(containsString("GLOBAL_QUOTE"))).andRespond(withSuccess(
                "{\"Information\":\"Thank you for using Alpha Vantage! Our standard API rate limit is 25 requests per day.\"}",
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.fetchStockPrice("IBM")).isInstanceOf(MarketDataUnavailableException.class);
        // The second call must fail fast without touching the network (only ONE request was expected).
        assertThatThrownBy(() -> provider.fetchStockPrice("IBM")).isInstanceOf(MarketDataUnavailableException.class);

        server.verify();
    }

    @Test
    void stopsCallingOnceTheDailyBudgetIsSpent() {
        build(2);
        String ok = "{\"Global Quote\":{\"05. price\":\"10.00\"}}";
        server.expect(ExpectedCount.times(2), requestTo(containsString("GLOBAL_QUOTE"))).andRespond(withSuccess(ok, MediaType.APPLICATION_JSON));

        provider.fetchStockPrice("A");
        provider.fetchStockPrice("B");
        assertThatThrownBy(() -> provider.fetchStockPrice("C")).isInstanceOf(MarketDataUnavailableException.class);

        server.verify(); // exactly 2 HTTP calls happened
    }
}
