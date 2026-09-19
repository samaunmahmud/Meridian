package com.meridian.backend.dto;

import com.meridian.backend.model.OrderType;

import java.math.BigDecimal;

// Pushed over the WebSocket when the system gives up on a pending order.
public record OrderRejectedMessage(
        String kind,          // always "ORDER_REJECTED"
        Long orderId,
        String symbol,
        OrderType type,
        BigDecimal quantity,
        String reason
) {
}
