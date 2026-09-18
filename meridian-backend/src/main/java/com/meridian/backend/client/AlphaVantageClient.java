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

    // Crypto quotes come from a different Alpha Vantage endpoint than stocks
    // (no GLOBAL_QUOTE for digital currencies) — this gives the live rate of
    // one unit of the crypto asset (e.g. BTC) priced in a fiat market (USD).
    public CurrencyExchangeRateResponse fetchCryptoQuote(String symbol, String market) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/query")
                        .queryParam("function", "CURRENCY_EXCHANGE_RATE")
                        .queryParam("from_currency", symbol)
                        .queryParam("to_currency", market)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(CurrencyExchangeRateResponse.class);
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
