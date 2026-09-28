package com.meridian.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** How the account's trading has gone, in USD. `winRate` is null until something has been sold. */
public record PerformanceResponse(
        BigDecimal realizedPnL,
        BigDecimal unrealizedPnL,
        BigDecimal totalPnL,
        BigDecimal feesPaid,
        int filledOrders,
        int closedTrades,
        int winningTrades,
        BigDecimal winRate,
        Trade bestTrade,
        Trade worstTrade,
        List<SymbolPerformance> bySymbol
) {
    public record Trade(Long orderId, String symbol, BigDecimal quantity, BigDecimal realizedPnL, Instant executedAt) {
    }

    /** Per stock: what selling it has realized, what the shares still held are up or down, and fees paid on it. */
    public record SymbolPerformance(String symbol, String name, BigDecimal realizedPnL, BigDecimal unrealizedPnL,
                                    BigDecimal totalPnL, BigDecimal feesPaid, int trades) {
    }
}
