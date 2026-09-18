package com.meridian.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceUpdateMessage(String kind, String symbol, BigDecimal price, Instant recordedAt) {
}
