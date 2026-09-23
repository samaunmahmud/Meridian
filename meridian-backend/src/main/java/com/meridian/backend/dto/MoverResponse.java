package com.meridian.backend.dto;

import com.meridian.backend.model.AssetType;

import java.math.BigDecimal;
import java.time.Instant;

// One ticker's move: the latest price against the price it is measured from (the previous close for a
// stock, 24 hours earlier for crypto). changePercent is rounded to two places.
public record MoverResponse(String symbol, String name, String exchange, AssetType assetType,
                            BigDecimal price, BigDecimal referencePrice,
                            BigDecimal change, BigDecimal changePercent,
                            Instant recordedAt, Instant referenceAt) {
}
