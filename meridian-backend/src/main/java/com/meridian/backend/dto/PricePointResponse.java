package com.meridian.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PricePointResponse(BigDecimal price, Instant recordedAt) {
}
