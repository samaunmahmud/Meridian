package com.meridian.backend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

// What one portfolio was paid for one dividend. Unique per (portfolio, dividend): never paid twice.
@Entity
@Table(
        name = "dividend_payments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "dividend_id"})
)
public class DividendPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dividend_id", nullable = false)
    private Dividend dividend;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal shares;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    public DividendPayment() {
    }

    public DividendPayment(Portfolio portfolio, Dividend dividend, BigDecimal shares, BigDecimal amount, Instant paidAt) {
        this.portfolio = portfolio;
        this.dividend = dividend;
        this.shares = shares;
        this.amount = amount;
        this.paidAt = paidAt;
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public Dividend getDividend() {
        return dividend;
    }

    public BigDecimal getShares() {
        return shares;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
