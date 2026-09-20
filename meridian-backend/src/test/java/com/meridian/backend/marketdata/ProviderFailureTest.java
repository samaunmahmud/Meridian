package com.meridian.backend.marketdata;

import com.meridian.backend.MutableClock;
import com.meridian.backend.client.AlphaVantageClient;
import com.meridian.backend.client.AlphaVantageProvider;
import com.meridian.backend.client.FinnhubProvider;
import com.meridian.backend.client.MarketDataProvider;
import com.meridian.backend.client.RequestBudget;
import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.exception.MarketDataUnreachableException;
import com.meridian.backend.model.SupportedCurrency;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * A price provider that is down must turn into ONE well-defined exception (which the schedulers log
 * in a line and the API reports as a 503), whichever way it fails, for both providers.
 */
class ProviderFailureTest {

    private record Setup(MarketDataProvider provider, MockRestServiceServer server) {
    }

    private static Setup finnhub() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MarketDataProperties props = new MarketDataProperties();
        props.setProvider("finnhub");
        RequestBudget budget = new RequestBudget(props, new MutableClock(Instant.parse("2026-09-19T10:00:00Z")));
        return new Setup(new FinnhubProvider(builder, budget, "k"), server);
    }

    private static Setup alphaVantage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RequestBudget budget = new RequestBudget(new MarketDataProperties(), new MutableClock(Instant.parse("2026-09-19T10:00:00Z")));
        return new Setup(new AlphaVantageProvider(new AlphaVantageClient(builder, "k"), budget), server);
    }

    private static void eachProvider(Consumer<Setup> check) {
        check.accept(finnhub());
        check.accept(alphaVantage());
    }

    private static void allCallsFail(Setup s) {
        assertThatThrownBy(() -> s.provider().fetchStockPrice("NVDA")).isInstanceOf(MarketDataUnreachableException.class);
        assertThatThrownBy(() -> s.provider().fetchCryptoPrice("BTC")).isInstanceOf(MarketDataUnreachableException.class);
        assertThatThrownBy(() -> s.provider().fetchUsdRate(SupportedCurrency.EUR)).isInstanceOf(MarketDataUnreachableException.class);
        assertThatThrownBy(() -> s.provider().searchSymbols("apple")).isInstanceOf(MarketDataUnreachableException.class);
    }

    @Test
    void aServerErrorIsReportedAsUnreachable() {
        eachProvider(s -> {
            s.server().expect(manyTimes(), anything()).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"error\":\"boom\"}"));
            allCallsFail(s);
        });
    }

    @Test
    void aRefusedRequestSuchAsABadKeyIsReportedAsUnreachableToo() {
        eachProvider(s -> {
            s.server().expect(manyTimes(), anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"error\":\"invalid key\"}"));
            allCallsFail(s);
        });
    }

    @Test
    void aReplyThatIsNotJsonIsReportedAsUnreachable() {
        eachProvider(s -> {
            s.server().expect(manyTimes(), anything()).andRespond(withSuccess("<html>Bad gateway</html>", MediaType.TEXT_HTML));
            allCallsFail(s);
        });
    }

    @Test
    void aConnectionFailureOrTimeoutIsReportedAsUnreachable() {
        eachProvider(s -> {
            s.server().expect(manyTimes(), anything()).andRespond(withException(new IOException("Connection reset")));
            allCallsFail(s);
        });
    }

    @Test
    void theErrorMessageIsSafeToShowAUserAndTheCauseIsKept() {
        Setup s = finnhub();
        s.server().expect(anything()).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> s.provider().fetchStockPrice("NVDA"))
                .isInstanceOf(MarketDataUnreachableException.class)
                .hasMessage("The market data provider is not responding. Try again in a few minutes.")
                .hasCauseInstanceOf(org.springframework.web.client.RestClientException.class);
    }
}
