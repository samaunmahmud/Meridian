package com.meridian.backend.dto;

import com.meridian.backend.model.AssetType;

public record TickerResponse(String symbol, String name, String exchange, AssetType assetType) {
}
