package com.meridian.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.meridian.backend.dto.NewsArticleResponse;
import com.meridian.backend.dto.TickerSearchResult;
import com.meridian.backend.exception.MarketDataUnavailableException;
import com.meridian.backend.exception.MarketDataUnreachableException;
import com.meridian.backend.model.SupportedCurrency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

// Alpha Vantage behind the MarketDataProvider interface. Every call is
// counted against the daily budget, and a "rate limit reached" reply is
// recognised as that (and remembered) instead of being read as "no data".
@Component
@ConditionalOnProperty(name = "marketdata.provider", havingValue = "alphavantage", matchIfMissing = true)
public class AlphaVantageProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageProvider.class);
    private static final int NEWS_LIMIT = 20;
    private static final DateTimeFormatter NEWS_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private final AlphaVantageClient client;
    private final RequestBudget budget;

    public AlphaVantageProvider(AlphaVantageClient client, RequestBudget budget) {
        this.client = client;
        this.budget = budget;
    }

    private <R extends AlphaVantageResponse> R call(Supplier<R> request) {
        if (!budget.tryAcquire()) {
            throw new MarketDataUnavailableException(
                    "Market data is paused: today's price-request allowance is used up. It resumes automatically.");
        }
        R response;
        try {
            response = request.get();
        } catch (RestClientException e) {
            // Timeout, connection refused, HTTP 4xx/5xx, or a reply that is not JSON.
            throw new MarketDataUnreachableException("The market data provider is not responding. Try again in a few minutes.", e);
        }
        String limitMessage = response == null ? null : response.rateLimitMessage();
        if (limitMessage != null) {
            budget.blockFor(limitMessage);
            log.warn("Alpha Vantage rate limit reached: {}", limitMessage);
            throw new MarketDataUnavailableException("The market data provider's rate limit was reached. Try again later.");
        }
        return response;
    }

    @Override
    public BigDecimal fetchStockPrice(String symbol) {
        GlobalQuoteResponse response = call(() -> client.fetchQuote(symbol));
        GlobalQuote quote = response == null ? null : response.getGlobalQuote();
        return parse(quote == null ? null : quote.getPrice(), symbol);
    }

    @Override
    public BigDecimal fetchCryptoPrice(String symbol) {
        CurrencyExchangeRateResponse response = call(() -> client.fetchCryptoQuote(symbol, "USD"));
        CurrencyExchangeRate rate = response == null ? null : response.getExchangeRate();
        return parse(rate == null ? null : rate.getExchangeRate(), symbol);
    }

    @Override
    public BigDecimal fetchUsdRate(SupportedCurrency currency) {
        CurrencyExchangeRateResponse response = call(() -> client.fetchExchangeRate(currency.name(), SupportedCurrency.USD.name()));
        CurrencyExchangeRate rate = response == null ? null : response.getExchangeRate();
        return parse(rate == null ? null : rate.getExchangeRate(), currency + "/USD");
    }

    @Override
    public List<TickerSearchResult> searchSymbols(String query) {
        SymbolSearchResponse response = call(() -> client.searchSymbols(query));
        if (response == null || response.getBestMatches() == null) return Collections.emptyList();
        return response.getBestMatches().stream()
                .map(m -> new TickerSearchResult(m.getSymbol(), m.getName(), m.getRegion()))
                .toList();
    }

    @Override
    public List<NewsArticleResponse> fetchNews(String symbol, boolean crypto) {
        String ticker = crypto ? "CRYPTO:" + symbol : symbol;
        NewsSentimentResponse response = call(() -> client.fetchNews(ticker, NEWS_LIMIT));
        List<NewsArticleResponse> articles = new ArrayList<>();
        if (response == null || response.getFeed() == null) return articles;
        for (JsonNode item : response.getFeed()) {
            String url = NewsLinks.web(item.path("url").asText(null));
            String headline = NewsLinks.text(item.path("title").asText(null));
            if (url == null || headline == null) continue;
            articles.add(new NewsArticleResponse(headline, NewsLinks.text(item.path("summary").asText(null)),
                    NewsLinks.text(item.path("source").asText(null)), url,
                    NewsLinks.secure(item.path("banner_image").asText(null)),
                    publishedAt(item.path("time_published").asText(null)), sentimentFor(item, ticker)));
        }
        return articles;
    }

    @Override
    public List<DividendEvent> fetchDividends(String symbol) {
        DividendsResponse response = call(() -> client.fetchDividends(symbol));
        List<DividendEvent> dividends = new ArrayList<>();
        if (response == null || response.getData() == null) return dividends;
        for (JsonNode item : response.getData()) {
            LocalDate exDate = date(item.path("ex_dividend_date").asText(null));
            BigDecimal amount = parse(item.path("amount").asText(null), symbol + " dividend");
            if (exDate == null || amount == null || amount.signum() <= 0) continue;
            LocalDate payDate = date(item.path("payment_date").asText(null));
            dividends.add(new DividendEvent(exDate, payDate == null ? exDate : payDate, amount));
        }
        return dividends;
    }

    private static LocalDate date(String raw) {
        if (raw == null) return null;
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return null; // "None"
        }
    }

    // "20260930T143000". The time zone is not documented; it is read as UTC, which can only make an article
    // look older than it is, never dated in the future.
    private static Instant publishedAt(String raw) {
        if (raw == null) return null;
        try {
            return LocalDateTime.parse(raw, NEWS_TIME).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // The rating of this ticker in the article ("Somewhat-Bullish" counts as bullish).
    private static String sentimentFor(JsonNode item, String ticker) {
        for (JsonNode rating : item.path("ticker_sentiment")) {
            if (!ticker.equalsIgnoreCase(rating.path("ticker").asText(""))) continue;
            String label = rating.path("ticker_sentiment_label").asText("");
            if (label.contains("Bullish")) return "Bullish";
            if (label.contains("Bearish")) return "Bearish";
            if (label.contains("Neutral")) return "Neutral";
        }
        return null;
    }

    private BigDecimal parse(String raw, String what) {
        if (raw == null || raw.isBlank()) {
            log.warn("No price data returned for {}", what);
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            log.warn("Malformed price '{}' returned for {}", raw, what);
            return null;
        }
    }
}
