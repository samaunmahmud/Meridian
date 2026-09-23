package com.meridian.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.meridian.backend.dto.TickerSearchResult;
import com.meridian.backend.exception.MarketDataUnavailableException;
import com.meridian.backend.exception.MarketDataUnreachableException;
import com.meridian.backend.model.SupportedCurrency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// Finnhub (https://finnhub.io) as the price source: a free key allows about
// 60 calls a minute, far more than Alpha Vantage's free daily allowance.
// Select it with marketdata.provider=finnhub and FINNHUB_API_KEY.
//
// Note: written against Finnhub's documented REST API and covered by tests
// with recorded-shape responses; it has not been run against the live service.
@Component
@ConditionalOnProperty(name = "marketdata.provider", havingValue = "finnhub")
public class FinnhubProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(FinnhubProvider.class);
    private static final int MAX_SEARCH_RESULTS = 10;
    // One /forex/rates reply has every currency, and a refresh asks for them one after another: reuse the
    // reply for this long instead of spending a request per currency. Far shorter than the FX refresh interval.
    static final Duration FOREX_REUSE = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final RequestBudget budget;
    private final Clock clock;
    private JsonNode forexRates;
    private Instant forexRatesAt;

    static final String DEFAULT_BASE_URL = "https://finnhub.io/api/v1";

    public FinnhubProvider(RestClient.Builder builder, RequestBudget budget, String apiKey) {
        this(builder, budget, apiKey, DEFAULT_BASE_URL, Clock.systemUTC());
    }

    public FinnhubProvider(RestClient.Builder builder, RequestBudget budget, String apiKey, String baseUrl) {
        this(builder, budget, apiKey, baseUrl, Clock.systemUTC());
    }

    // The base URL is a setting only so tests and a stand-in server can replace it.
    @Autowired
    public FinnhubProvider(RestClient.Builder builder, RequestBudget budget, @Value("${FINNHUB_API_KEY}") String apiKey,
                           @Value("${marketdata.finnhub.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
                           Clock clock) {
        this.restClient = builder.baseUrl(baseUrl).defaultHeader("X-Finnhub-Token", apiKey).build();
        this.budget = budget;
        this.clock = clock;
    }

    private JsonNode get(Function<org.springframework.web.util.UriBuilder, java.net.URI> uri) {
        if (!budget.tryAcquire()) {
            throw new MarketDataUnavailableException(
                    "Market data is paused: today's price-request allowance is used up. It resumes automatically.");
        }
        try {
            return restClient.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            budget.blockFor("per minute");
            log.warn("Finnhub rate limit reached");
            throw new MarketDataUnavailableException("The market data provider's rate limit was reached. Try again later.");
        } catch (RestClientException e) {
            // Timeout, connection refused, HTTP 4xx/5xx, or a reply that is not JSON.
            throw new MarketDataUnreachableException("The market data provider is not responding. Try again in a few minutes.", e);
        }
    }

    @Override
    public BigDecimal fetchStockPrice(String symbol) {
        return quote(symbol);
    }

    // Crypto is quoted on an exchange pair, e.g. BTC -> BINANCE:BTCUSDT.
    @Override
    public BigDecimal fetchCryptoPrice(String symbol) {
        return quote("BINANCE:" + symbol + "USDT");
    }

    private BigDecimal quote(String finnhubSymbol) {
        JsonNode body = get(u -> u.path("/quote").queryParam("symbol", finnhubSymbol).build());
        double current = body == null ? 0 : body.path("c").asDouble(0);
        if (current <= 0) { // Finnhub answers unknown symbols with zeros
            log.warn("No price data returned for {}", finnhubSymbol);
            return null;
        }
        return BigDecimal.valueOf(current);
    }

    // /forex/rates?base=USD gives "how many EUR per 1 USD"; we want USD per 1 EUR.
    @Override
    public BigDecimal fetchUsdRate(SupportedCurrency currency) {
        JsonNode body = forexRates();
        double perUsd = body == null ? 0 : body.path("quote").path(currency.name()).asDouble(0);
        if (perUsd <= 0) {
            log.warn("No FX rate returned for {}/USD", currency);
            return null;
        }
        return BigDecimal.ONE.divide(BigDecimal.valueOf(perUsd), 8, RoundingMode.HALF_UP);
    }

    private synchronized JsonNode forexRates() {
        Instant now = clock.instant();
        if (forexRates == null || !now.isBefore(forexRatesAt.plus(FOREX_REUSE))) {
            forexRates = get(u -> u.path("/forex/rates").queryParam("base", "USD").build());
            forexRatesAt = now;
        }
        return forexRates;
    }

    @Override
    public List<TickerSearchResult> searchSymbols(String query) {
        JsonNode body = get(u -> u.path("/search").queryParam("q", query).build());
        List<TickerSearchResult> results = new ArrayList<>();
        if (body == null) return results;
        for (JsonNode item : body.path("result")) {
            if (results.size() >= MAX_SEARCH_RESULTS) break;
            String symbol = item.path("symbol").asText("");
            if (symbol.isBlank()) continue;
            results.add(new TickerSearchResult(symbol, item.path("description").asText(symbol), item.path("type").asText("")));
        }
        return results;
    }
}
