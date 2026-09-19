package com.meridian.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GlobalQuoteResponse extends AlphaVantageResponse {

    @JsonProperty("Global Quote")
    private GlobalQuote globalQuote;

    public GlobalQuote getGlobalQuote() {
        return globalQuote;
    }

    public void setGlobalQuote(GlobalQuote globalQuote) {
        this.globalQuote = globalQuote;
    }
}
