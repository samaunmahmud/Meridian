package com.meridian.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

// Alpha Vantage DIVIDENDS: {"symbol": "IBM", "data": [{"ex_dividend_date": "2026-08-08", "declaration_date": ...,
// "record_date": ..., "payment_date": "2026-09-10", "amount": "1.68"}]}. Unknown dates are the text "None".
public class DividendsResponse extends AlphaVantageResponse {

    @JsonProperty("data")
    private List<JsonNode> data;

    public List<JsonNode> getData() {
        return data;
    }
}
