package com.meridian.backend.dto;

import com.meridian.backend.model.RecurringFrequency;
import com.meridian.backend.model.SupportedCurrency;
import java.math.BigDecimal;
import java.time.Instant;

public record RecurringOrderResponse(
        Long id,
        String symbol,
        BigDecimal amount,
        SupportedCurrency settlementCurrency,
        RecurringFrequency frequency,
        Instant nextRunAt,
        boolean active,
        Instant createdAt
) {
}
