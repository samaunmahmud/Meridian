package com.meridian.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;

// Alpha Vantage reports errors and rate limits as a normal 200 response whose
// body has a "Note" or "Information" field instead of data.
public abstract class AlphaVantageResponse {

    @JsonProperty("Note")
    private String note;

    @JsonProperty("Information")
    private String information;

    /** The provider's rate-limit / plan message, or null if this is a normal response. */
    public String rateLimitMessage() {
        if (note != null && !note.isBlank()) return note;
        if (information != null && !information.isBlank()) return information;
        return null;
    }
}
