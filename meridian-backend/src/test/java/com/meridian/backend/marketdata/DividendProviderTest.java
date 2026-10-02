package com.meridian.backend.marketdata;

import com.meridian.backend.MutableClock;
import com.meridian.backend.client.AlphaVantageClient;
import com.meridian.backend.client.AlphaVantageProvider;
import com.meridian.backend.client.DividendEvent;
import com.meridian.backend.client.FinnhubProvider;
import com.meridian.backend.client.RequestBudget;
import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.exception.MarketDataUnreachableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DividendProviderTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    private MockRestServiceServer server;

    private AlphaVantageProvider alphaVantage() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new AlphaVantageProvider(new AlphaVantageClient(builder, "test-key"),
                new RequestBudget(new MarketDataProperties(), new MutableClock(NOW)));
    }

    private FinnhubProvider finnhub() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MarketDataProperties props = new MarketDataProperties();
        props.setProvider("finnhub");
        MutableClock clock = new MutableClock(NOW);
        return new FinnhubProvider(builder, new RequestBudget(props, clock), "secret-key", "https://finnhub.io/api/v1", clock);
    }

    @Test
    void alphaVantageReadsDividendsAndFallsBackToTheExDateWhenThePaymentDateIsUnknown() {
        AlphaVantageProvider provider = alphaVantage();
        server.expect(requestTo(allOf(containsString("function=DIVIDENDS"), containsString("symbol=IBM"))))
                .andRespond(withSuccess("""
                        {"symbol":"IBM","data":[
                          {"ex_dividend_date":"2026-11-07","declaration_date":"2026-10-28","record_date":"2026-11-07","payment_date":"None","amount":"1.69"},
                          {"ex_dividend_date":"2026-08-08","declaration_date":"2026-07-29","record_date":"2026-08-08","payment_date":"2026-09-10","amount":"1.68"},
                          {"ex_dividend_date":"None","payment_date":"2026-06-10","amount":"1.67"},
                          {"ex_dividend_date":"2026-05-09","payment_date":"2026-06-10","amount":"0"}
                        ]}""", MediaType.APPLICATION_JSON));

        assertThat(provider.fetchDividends("IBM")).containsExactly(
                new DividendEvent(LocalDate.parse("2026-11-07"), LocalDate.parse("2026-11-07"), new java.math.BigDecimal("1.69")),
                new DividendEvent(LocalDate.parse("2026-08-08"), LocalDate.parse("2026-09-10"), new java.math.BigDecimal("1.68")));
    }

    @Test
    void alphaVantageWithNoDividendsGivesAnEmptyList() {
        AlphaVantageProvider provider = alphaVantage();
        server.expect(requestTo(containsString("DIVIDENDS"))).andRespond(withSuccess("{\"symbol\":\"TSLA\",\"data\":[]}", MediaType.APPLICATION_JSON));
        assertThat(provider.fetchDividends("TSLA")).isEmpty();
    }

    @Test
    void finnhubReadsUsdDividendsOnly() {
        FinnhubProvider provider = finnhub();
        server.expect(requestTo(allOf(containsString("/stock/dividend?symbol=AAPL"), containsString("from=2025-10-02"), containsString("to=2027-10-02"))))
                .andRespond(withSuccess("""
                        [{"symbol":"AAPL","date":"2026-08-11","amount":0.26,"adjustedAmount":0.26,"payDate":"2026-08-14","currency":"USD"},
                         {"symbol":"AAPL","date":"2026-05-12","amount":0.25,"payDate":"","currency":"USD"},
                         {"symbol":"AAPL","date":"2026-02-10","amount":0.24,"payDate":"2026-02-13","currency":"EUR"}]""", MediaType.APPLICATION_JSON));

        assertThat(provider.fetchDividends("AAPL")).extracting(DividendEvent::payDate)
                .containsExactly(LocalDate.parse("2026-08-14"), LocalDate.parse("2026-05-12"));
    }

    @Test
    void finnhubOnAFreePlanMeansNoDividendsNotAnOutage() {
        FinnhubProvider provider = finnhub();
        server.expect(requestTo(containsString("/stock/dividend"))).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThat(provider.fetchDividends("AAPL")).isEmpty();
    }

    @Test
    void finnhubServerErrorsAreStillOutages() {
        FinnhubProvider provider = finnhub();
        server.expect(requestTo(containsString("/stock/dividend"))).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> provider.fetchDividends("AAPL")).isInstanceOf(MarketDataUnreachableException.class);
    }
}
