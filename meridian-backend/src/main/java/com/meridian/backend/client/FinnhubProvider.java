package com.meridian.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.meridian.backend.dto.NewsArticleResponse;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// Finnhub (https://finnhub.io) as the price source: a free key allows about
// 60 calls a minute, far more than Alpha Vantage's free daily allowance.
// Select it with marketdata.provider=finnhub and FINNHUB_API_KEY.
//
// Exchange rates are a paid Finnhub feature (/forex/rates answers 403 on a free
// key), so they come from Frankfurter (https://frankfurter.dev) instead: the
// European Central Bank's reference rates, free and keyless, published once per
// working day. Those requests carry no Finnhub key and don't count against its budget.
@Component
@ConditionalOnProperty(name = "marketdata.provider", havingValue = "finnhub")
public class FinnhubProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(FinnhubProvider.class);
    private static final int MAX_SEARCH_RESULTS = 10;
    // One rates reply has every currency, and a refresh asks for them one after another: reuse the
    // reply for this long instead of spending a request per currency. Far shorter than the FX refresh interval.
    static final Duration FOREX_REUSE = Duration.ofSeconds(60);
    // How far back company news is asked for.
    static final int NEWS_DAYS = 7;

    private final RestClient restClient;
    private final RequestBudget budget;
    private final Clock clock;
    private JsonNode forexRates;
    private Instant forexRatesAt;

    static final String DEFAULT_BASE_URL = "https://finnhub.io/api/v1";
    static final String DEFAULT_FX_BASE_URL = "https://api.frankfurter.dev/v1";

    private final RestClient fxClient;

    public FinnhubProvider(RestClient.Builder builder, RequestBudget budget, String apiKey) {
        this(builder, budget, apiKey, DEFAULT_BASE_URL, Clock.systemUTC());
    }

    public FinnhubProvider(RestClient.Builder builder, RequestBudget budget, String apiKey, String baseUrl) {
        this(builder, budget, apiKey, baseUrl, Clock.systemUTC());
    }

    public FinnhubProvider(RestClient.Builder builder, RequestBudget budget, String apiKey, String baseUrl, Clock clock) {
        this(builder, budget, apiKey, baseUrl, DEFAULT_FX_BASE_URL, clock);
    }

    // The base URLs are settings only so tests and a stand-in server can replace them.
    @Autowired
    public FinnhubProvider(RestClient.Builder builder, RequestBudget budget, @Value("${FINNHUB_API_KEY}") String apiKey,
                           @Value("${marketdata.finnhub.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
                           @Value("${marketdata.fx.base-url:" + DEFAULT_FX_BASE_URL + "}") String fxBaseUrl,
                           Clock clock) {
        // Cloned before the Finnhub key is added, so the key never goes to Frankfurter.
        this.fxClient = builder.clone().baseUrl(fxBaseUrl).build();
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

    // /latest?base=USD gives "how many EUR per 1 USD"; we want USD per 1 EUR.
    @Override
    public BigDecimal fetchUsdRate(SupportedCurrency currency) {
        JsonNode body = forexRates();
        double perUsd = body == null ? 0 : body.path("rates").path(currency.name()).asDouble(0);
        if (perUsd <= 0) {
            log.warn("No FX rate returned for {}/USD", currency);
            return null;
        }
        return BigDecimal.ONE.divide(BigDecimal.valueOf(perUsd), 8, RoundingMode.HALF_UP);
    }

    private synchronized JsonNode forexRates() {
        Instant now = clock.instant();
        if (forexRates == null || !now.isBefore(forexRatesAt.plus(FOREX_REUSE))) {
            try {
                forexRates = fxClient.get()
                        .uri(u -> u.path("/latest").queryParam("base", "USD").queryParam("symbols", "EUR,GBP").build())
                        .retrieve().body(JsonNode.class);
            } catch (RestClientException e) {
                throw new MarketDataUnreachableException("The exchange rate service is not responding. Try again in a few minutes.", e);
            }
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

    // /stock/dividend for the last year and what is announced. Finnhub sells this on paid plans only: a free key
    // gets 403, which means "no dividends from this provider" rather than an outage.
    @Override
    public List<DividendEvent> fetchDividends(String symbol) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        JsonNode body;
        try {
            body = get(u -> u.path("/stock/dividend").queryParam("symbol", symbol)
                    .queryParam("from", today.minusYears(1).toString()).queryParam("to", today.plusYears(1).toString()).build());
        } catch (MarketDataUnreachableException e) {
            if (e.getCause() instanceof HttpClientErrorException.Forbidden) {
                log.info("Finnhub dividends need a paid plan; none for {}", symbol);
                return List.of();
            }
            throw e;
        }
        List<DividendEvent> dividends = new ArrayList<>();
        if (body == null) return dividends;
        for (JsonNode item : body) {
            String currency = item.path("currency").asText("USD");
            double amount = item.path("amount").asDouble(0);
            LocalDate exDate = date(item.path("date").asText(null));
            if (exDate == null || amount <= 0 || !"USD".equalsIgnoreCase(currency)) continue;
            LocalDate payDate = date(item.path("payDate").asText(null));
            dividends.add(new DividendEvent(exDate, payDate == null ? exDate : payDate, BigDecimal.valueOf(amount)));
        }
        return dividends;
    }

    private static LocalDate date(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    // /company-news for a stock (dates are required); crypto has no per-coin feed, so it gets the crypto category.
    @Override
    public List<NewsArticleResponse> fetchNews(String symbol, boolean crypto) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        JsonNode body = crypto
                ? get(u -> u.path("/news").queryParam("category", "crypto").build())
                : get(u -> u.path("/company-news").queryParam("symbol", symbol)
                        .queryParam("from", today.minusDays(NEWS_DAYS).toString()).queryParam("to", today.toString()).build());
        List<NewsArticleResponse> articles = new ArrayList<>();
        if (body == null) return articles;
        for (JsonNode item : body) {
            String url = NewsLinks.web(item.path("url").asText(null));
            String headline = NewsLinks.text(item.path("headline").asText(null));
            if (url == null || headline == null) continue;
            long seconds = item.path("datetime").asLong(0);
            articles.add(new NewsArticleResponse(headline, NewsLinks.text(item.path("summary").asText(null)),
                    NewsLinks.text(item.path("source").asText(null)), url, NewsLinks.secure(item.path("image").asText(null)),
                    seconds > 0 ? Instant.ofEpochSecond(seconds) : null, null));
        }
        return articles;
    }
}
