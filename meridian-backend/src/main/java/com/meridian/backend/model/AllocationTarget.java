package com.meridian.backend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

// The share (in %) of the portfolio's USD value a stock should make up. Cash is the remainder.
@Entity
@Table(
        name = "allocation_targets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "ticker_id"})
)
public class AllocationTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Column(name = "target_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal targetPercent;

    public AllocationTarget() {
    }

    public AllocationTarget(Portfolio portfolio, Ticker ticker, BigDecimal targetPercent) {
        this.portfolio = portfolio;
        this.ticker = ticker;
        this.targetPercent = targetPercent;
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public BigDecimal getTargetPercent() {
        return targetPercent;
    }
}
