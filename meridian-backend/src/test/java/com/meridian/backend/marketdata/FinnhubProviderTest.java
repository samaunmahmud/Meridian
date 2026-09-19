package com.meridian.backend.marketdata;

import com.meridian.backend.MutableClock;
import com.meridian.backend.client.FinnhubProvider;
import com.meridian.backend.client.RequestBudget;
import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.dto.TickerSearchResult;
import com.meridian.backend.exception.MarketDataUnavailableException;
import com.meridian.backend.model.SupportedCurrency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FinnhubProviderTest {

    private MockRestServiceServer server;
    private FinnhubProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MarketDataProperties props = new MarketDataProperties();
        props.setProvider("finnhub");
        RequestBudget budget = new RequestBudget(props, new MutableClock(Instant.parse("2026-09-19T10:00:00Z")));
        provider = new FinnhubProvider(builder, budget, "secret-key");
    }

    @Test
    void readsTheCurrentPriceAndSendsTheApiKeyAsAHeader() {
        server.expect(requestTo(containsString("/quote?symbol=NVDA")))
                .andExpect(header("X-Finnhub-Token", "secret-key"))
                .andRespond(withSuccess("{\"c\":482.31,\"d\":1.2,\"dp\":0.25,\"h\":490,\"l\":470,\"o\":475,\"pc\":481.11}", MediaType.APPLICATION_JSON));

        assertThat(provider.fetchStockPrice("NVDA")).isEqualByComparingTo("482.31");
    }

    @Test
    void unknownSymbolsComeBackAsZerosAndBecomeNull() {
        server.expect(requestTo(containsString("/quote"))).andRespond(withSuccess("{\"c\":0,\"d\":null,\"dp\":null}", MediaType.APPLICATION_JSON));

        assertThat(provider.fetchStockPrice("NOPE")).isNull();
    }

    @Test
    void cryptoIsQuotedOnAnExchangePair() {
        server.expect(requestTo(containsString("symbol=BINANCE:BTCUSDT"))).andRespond(withSuccess("{\"c\":96420.5}", MediaType.APPLICATION_JSON));

        assertThat(provider.fetchCryptoPrice("BTC")).isEqualByComparingTo("96420.5");
    }

    @Test
    void invertsTheForexQuoteIntoUsdPerUnit() {
        // Finnhub says 0.9174... EUR per 1 USD; we need USD per 1 EUR (~1.09)
        server.expect(requestTo(containsString("/forex/rates?base=USD")))
                .andRespond(withSuccess("{\"base\":\"USD\",\"quote\":{\"EUR\":0.9174311926,\"GBP\":0.7874015748}}", MediaType.APPLICATION_JSON));

        BigDecimal eurInUsd = provider.fetchUsdRate(SupportedCurrency.EUR);

        assertThat(eurInUsd.doubleValue()).isCloseTo(1.09, within(0.0001));
    }

    @Test
    void mapsSearchResults() {
        server.expect(requestTo(containsString("/search?q=apple"))).andRespond(withSuccess(
                "{\"count\":2,\"result\":[{\"description\":\"APPLE INC\",\"displaySymbol\":\"AAPL\",\"symbol\":\"AAPL\",\"type\":\"Common Stock\"},{\"description\":\"\",\"symbol\":\"\"}]}",
                MediaType.APPLICATION_JSON));

        List<TickerSearchResult> results = provider.searchSymbols("apple");

        assertThat(results).containsExactly(new TickerSearchResult("AAPL", "APPLE INC", "Common Stock"));
    }

    @Test
    void aTooManyRequestsReplyBlocksFurtherCalls() {
        server.expect(ExpectedCount.once(), requestTo(containsString("/quote"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.fetchStockPrice("NVDA")).isInstanceOf(MarketDataUnavailableException.class);
        assertThatThrownBy(() -> provider.fetchStockPrice("NVDA")).isInstanceOf(MarketDataUnavailableException.class);

        server.verify(); // only the first call reached the network
    }
}
