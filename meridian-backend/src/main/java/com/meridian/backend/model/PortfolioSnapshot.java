package com.meridian.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

// A time-series record of "what was this portfolio worth at this moment."
// Taken periodically by a scheduled job, this is what powers a REAL equity
// curve chart — actual historical values, not invented data.
@Entity
@Table(
        name = "portfolio_snapshots",
        indexes = @Index(name = "idx_portfolio_recorded_at", columnList = "portfolio_id, recorded_at")
)
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "total_value", nullable = false, precision = 14, scale = 4)
    private BigDecimal totalValue;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public PortfolioSnapshot() {
    }

    public PortfolioSnapshot(Portfolio portfolio, BigDecimal totalValue, Instant recordedAt) {
        this.portfolio = portfolio;
        this.totalValue = totalValue;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
