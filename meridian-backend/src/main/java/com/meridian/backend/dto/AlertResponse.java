package com.meridian.backend.dto;

import com.meridian.backend.model.AlertDirection;
import java.math.BigDecimal;
import java.time.Instant;

public record AlertResponse(
        Long id,
        String symbol,
        AlertDirection direction,
        BigDecimal targetPrice,
        boolean triggered,
        Instant createdAt,
        Instant triggeredAt,
        BigDecimal movePercent,     // null for an alert set at a price
        BigDecimal referencePrice   // the price a percentage alert was measured from
) {
}
