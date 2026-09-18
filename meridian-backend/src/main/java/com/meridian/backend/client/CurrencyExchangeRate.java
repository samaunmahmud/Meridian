package com.meridian.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CurrencyExchangeRate {

    @JsonProperty("1. From_Currency Code")
    private String fromCurrencyCode;

    @JsonProperty("3. To_Currency Code")
    private String toCurrencyCode;

    @JsonProperty("5. Exchange Rate")
    private String exchangeRate;

    public String getFromCurrencyCode() {
        return fromCurrencyCode;
    }

    public void setFromCurrencyCode(String fromCurrencyCode) {
        this.fromCurrencyCode = fromCurrencyCode;
    }

    public String getToCurrencyCode() {
        return toCurrencyCode;
    }

    public void setToCurrencyCode(String toCurrencyCode) {
        this.toCurrencyCode = toCurrencyCode;
    }

    public String getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(String exchangeRate) {
        this.exchangeRate = exchangeRate;
    }
}
