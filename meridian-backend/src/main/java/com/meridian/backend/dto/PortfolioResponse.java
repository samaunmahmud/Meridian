package com.meridian.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(
        BigDecimal cashBalance,
        BigDecimal reservedCash,
        BigDecimal availableCash,
        BigDecimal holdingsValue,
        BigDecimal totalValue,
        List<HoldingResponse> holdings
) {
}
