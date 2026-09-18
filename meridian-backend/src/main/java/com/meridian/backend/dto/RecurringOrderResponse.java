package com.meridian.backend.dto;

import com.meridian.backend.model.RecurringFrequency;
import java.math.BigDecimal;
import java.time.Instant;

public record RecurringOrderResponse(
        Long id,
        String symbol,
        BigDecimal amount,
        RecurringFrequency frequency,
        Instant nextRunAt,
        boolean active,
        Instant createdAt
) {
}
