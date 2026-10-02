package com.meridian.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

// Alpha Vantage NEWS_SENTIMENT: {"items": "50", "feed": [{title, url, time_published, summary, source,
// banner_image, ticker_sentiment: [{ticker, ticker_sentiment_label}]}]}. Kept as JSON nodes: the feed is wide
// and only a few fields are read.
public class NewsSentimentResponse extends AlphaVantageResponse {

    @JsonProperty("feed")
    private List<JsonNode> feed;

    public List<JsonNode> getFeed() {
        return feed;
    }
}
