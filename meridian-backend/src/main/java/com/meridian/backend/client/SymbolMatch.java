package com.meridian.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SymbolMatch {

    @JsonProperty("1. symbol")
    private String symbol;

    @JsonProperty("2. name")
    private String name;

    @JsonProperty("4. region")
    private String region;

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }
}
