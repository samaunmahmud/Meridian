package com.meridian.backend.dto;

import com.meridian.backend.model.AssetType;
import java.math.BigDecimal;
import java.time.Instant;

public record WatchlistItemResponse(
        String symbol,
        String name,
        String exchange,
        AssetType assetType,
        BigDecimal currentPrice,
        Instant addedAt
) {
}
