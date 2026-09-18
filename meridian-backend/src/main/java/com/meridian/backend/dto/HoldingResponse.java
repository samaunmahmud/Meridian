package com.meridian.backend.dto;

import java.math.BigDecimal;

public record HoldingResponse(
        String symbol,
        String name,
        BigDecimal quantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        BigDecimal avgCost,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal gainLoss,
        BigDecimal gainLossPct
) {
}
