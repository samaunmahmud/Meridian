package com.meridian.backend.dto;

import com.meridian.backend.model.AssetType;

public record AddTickerRequest(String symbol, String name, String exchange, AssetType assetType) {
}
