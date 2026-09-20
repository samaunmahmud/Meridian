package com.meridian.backend.client;

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
