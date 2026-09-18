package com.meridian.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CurrencyExchangeRateResponse {

    @JsonProperty("Realtime Currency Exchange Rate")
    private CurrencyExchangeRate exchangeRate;

    public CurrencyExchangeRate getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(CurrencyExchangeRate exchangeRate) {
        this.exchangeRate = exchangeRate;
    }
}
