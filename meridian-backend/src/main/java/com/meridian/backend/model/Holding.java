package com.meridian.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(
        name = "holdings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "ticker_id"})
)
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    // Shares locked up by open SELL limit/stop-loss orders — held out of the
    // sellable quantity so the same shares can't be promised to two orders.
    @Column(name = "reserved_quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "avg_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal avgCost;

    public Holding() {
    }

    public Holding(Portfolio portfolio, Ticker ticker, BigDecimal quantity, BigDecimal avgCost) {
        this.portfolio = portfolio;
        this.ticker = ticker;
        this.quantity = quantity;
        this.avgCost = avgCost;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(BigDecimal reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public BigDecimal getAvailableQuantity() {
        return quantity.subtract(reservedQuantity);
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public void setAvgCost(BigDecimal avgCost) {
        this.avgCost = avgCost;
    }
}
