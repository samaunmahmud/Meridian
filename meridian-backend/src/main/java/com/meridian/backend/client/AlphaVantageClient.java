package com.meridian.backend.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AlphaVantageClient {

    private final RestClient restClient;

    @Value("${ALPHA_VANTAGE_API_KEY}")
    private String apiKey;

    public AlphaVantageClient(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://www.alphavantage.co").build();
    }

    public GlobalQuoteResponse fetchQuote(String symbol) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/query")
                        .queryParam("function", "GLOBAL_QUOTE")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(GlobalQuoteResponse.class);
    }

    // CURRENCY_EXCHANGE_RATE works for any from/to pair, fiat or crypto —
    // this is the one underlying call both crypto pricing and fiat FX rates
    // are built on.
    public CurrencyExchangeRateResponse fetchExchangeRate(String from, String to) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/query")
                        .queryParam("function", "CURRENCY_EXCHANGE_RATE")
                        .queryParam("from_currency", from)
                        .queryParam("to_currency", to)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(CurrencyExchangeRateResponse.class);
    }

    // Crypto quotes come from the same endpoint as fiat FX rates (no
    // GLOBAL_QUOTE for digital currencies) — this gives the live rate of
    // one unit of the crypto asset (e.g. BTC) priced in a fiat market (USD).
    public CurrencyExchangeRateResponse fetchCryptoQuote(String symbol, String market) {
        return fetchExchangeRate(symbol, market);
    }

    // Lets a user search by company name or partial ticker (e.g. "apple" or
    // "AAP") and get back real matching symbols with their actual names —
    // this is what makes "add any stock" possible instead of a fixed list.
    public SymbolSearchResponse searchSymbols(String keywords) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/query")
                        .queryParam("function", "SYMBOL_SEARCH")
                        .queryParam("keywords", keywords)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(SymbolSearchResponse.class);
    }
}
