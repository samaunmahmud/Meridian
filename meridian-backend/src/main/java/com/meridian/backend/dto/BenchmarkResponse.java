package com.meridian.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The portfolio's return next to one ticker's over the same window, both in % since the window's first point.
 * The portfolio's return is time-weighted: deposits, withdrawals and currency moves are not counted as gains.
 * `benchmark` is null at points before the ticker had a price; the totals are null when there is nothing to compare.
 */
public record BenchmarkResponse(
        String symbol,
        String name,
        BigDecimal portfolioReturn,
        BigDecimal benchmarkReturn,
        List<Point> points
) {
    public record Point(Instant at, BigDecimal portfolio, BigDecimal benchmark) {
    }
}
