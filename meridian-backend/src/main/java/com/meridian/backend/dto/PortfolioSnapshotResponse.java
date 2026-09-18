package com.meridian.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioSnapshotResponse(BigDecimal totalValue, Instant recordedAt) {
}
