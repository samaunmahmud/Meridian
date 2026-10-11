package com.meridian.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

// amountPutIn: the money put into the USD total by recordedAt (see BenchmarkService.amountPutIn), so a chart
// can tell what the value gained over a window from what was deposited or withdrawn in it. Null when unknown.
public record PortfolioSnapshotResponse(BigDecimal totalValue, Instant recordedAt, BigDecimal amountPutIn) {

    public PortfolioSnapshotResponse(BigDecimal totalValue, Instant recordedAt) {
        this(totalValue, recordedAt, null);
    }
}
