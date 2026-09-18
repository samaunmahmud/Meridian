package com.meridian.backend.dto;

import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderType;
import java.math.BigDecimal;

public record OrderRequest(
        String symbol,
        OrderType type,
        OrderKind kind,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal stopPrice
) {
}
