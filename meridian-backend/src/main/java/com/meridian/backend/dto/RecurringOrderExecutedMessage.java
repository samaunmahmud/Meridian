package com.meridian.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RecurringOrderExecutedMessage(
        String kind,          // always "RECURRING_ORDER_EXECUTED"
        Long recurringOrderId,
        String symbol,
        BigDecimal quantity,
        BigDecimal price,
        Instant executedAt
) {
}
