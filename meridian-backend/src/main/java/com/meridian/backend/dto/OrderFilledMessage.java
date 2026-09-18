package com.meridian.backend.dto;

import com.meridian.backend.model.OrderType;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderFilledMessage(
        String kind,          // always "ORDER_FILLED" — lets the frontend tell this apart from a plain price update
        Long orderId,
        String symbol,
        OrderType type,
        BigDecimal quantity,
        BigDecimal price,
        Instant executedAt
) {
}
