package com.meridian.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SymbolSearchResponse extends AlphaVantageResponse {

    @JsonProperty("bestMatches")
    private List<SymbolMatch> bestMatches;

    public List<SymbolMatch> getBestMatches() {
        return bestMatches;
    }
}
