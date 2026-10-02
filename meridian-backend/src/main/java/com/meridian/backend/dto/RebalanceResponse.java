package com.meridian.backend.dto;

import com.meridian.backend.model.OrderType;

import java.math.BigDecimal;
import java.util.List;

/**
 * The portfolio (USD cash plus holdings, in USD) next to its target allocation, and the trades that would bring
 * it back. Rows are every holding and every stock with a target; a holding without a target has a target of 0.
 * `trade` is null when a row is within {@code toleranceBand} percentage points of its target, its trade would be
 * tiny, or it has no price. Buys are sized to leave room for the commission. `hasTargets` is false until targets
 * are set (then every target is 0 and no trades are suggested).
 */
public record RebalanceResponse(
        boolean hasTargets,
        BigDecimal totalValue,
        BigDecimal toleranceBand,
        Cash cash,
        List<Row> rows
) {
    public record Cash(BigDecimal value, BigDecimal currentPercent, BigDecimal targetPercent) {
    }

    public record Row(String symbol, String name, BigDecimal price, BigDecimal shares, BigDecimal value,
                      BigDecimal currentPercent, BigDecimal targetPercent, Trade trade) {
    }

    public record Trade(OrderType type, BigDecimal quantity, BigDecimal estimatedValue) {
    }
}
