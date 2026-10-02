package com.meridian.backend.dto;

import java.time.Instant;

/**
 * One news article. `url` is always http(s); `imageUrl` is https or null; `sentiment` is "Bullish", "Bearish",
 * "Neutral" or null when the provider does not rate it.
 */
public record NewsArticleResponse(String headline, String summary, String source, String url, String imageUrl,
                                  Instant publishedAt, String sentiment) {
}
