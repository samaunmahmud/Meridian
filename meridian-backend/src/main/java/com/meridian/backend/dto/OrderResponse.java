package com.meridian.backend.dto;

import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderStatus;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.SupportedCurrency;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        String symbol,
        OrderType type,
        OrderKind kind,
        OrderStatus status,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal stopPrice,
        BigDecimal price,
        BigDecimal feeAmount,
        Instant createdAt,
        Instant executedAt,
        BigDecimal realizedPnL,
        String rejectionReason,
        SupportedCurrency settlementCurrency,   // wallet used, or null for USD
        BigDecimal settlementAmount             // amount paid / received in that currency
) {
}
