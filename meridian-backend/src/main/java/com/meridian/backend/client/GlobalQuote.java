package com.meridian.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GlobalQuote {

    @JsonProperty("01. symbol")
    private String symbol;

    @JsonProperty("05. price")
    private String price;

    @JsonProperty("07. latest trading day")
    private String latestTradingDay;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getLatestTradingDay() {
        return latestTradingDay;
    }

    public void setLatestTradingDay(String latestTradingDay) {
        this.latestTradingDay = latestTradingDay;
    }
}
