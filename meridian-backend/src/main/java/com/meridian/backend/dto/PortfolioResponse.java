package com.meridian.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param amountPutIn the money that has gone into the USD total: the starting cash plus USD deposits and conversions
 *                    into USD, less withdrawals and conversions out (so totalValue minus this is the real gain). Only
 *                    GET /api/portfolio fills it in; it is null elsewhere.
 */
public record PortfolioResponse(
        BigDecimal cashBalance,
        BigDecimal reservedCash,
        BigDecimal availableCash,
        BigDecimal holdingsValue,
        BigDecimal totalValue,
        List<HoldingResponse> holdings,
        BigDecimal amountPutIn
) {
    public PortfolioResponse withAmountPutIn(BigDecimal amount) {
        return new PortfolioResponse(cashBalance, reservedCash, availableCash, holdingsValue, totalValue, holdings, amount);
    }
}
