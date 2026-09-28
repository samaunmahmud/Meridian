package com.meridian.backend.dto;

import java.math.BigDecimal;

/** New terms for a pending order. Only the price field that fits the order's kind is read. */
public record ReplaceOrderRequest(BigDecimal quantity, BigDecimal limitPrice, BigDecimal stopPrice, BigDecimal trailPercent) {
}
