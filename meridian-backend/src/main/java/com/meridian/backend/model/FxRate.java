package com.meridian.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

// Latest known rate to convert 1 unit of baseCurrency into quoteCurrency.
// Only baseCurrency/USD pairs are actually polled — any other pair is
// derived via USD as the common leg (see FxRateService.getRate).
@Entity
@Table(
        name = "fx_rates",
        uniqueConstraints = @UniqueConstraint(columnNames = {"base_currency", "quote_currency"})
)
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_currency", nullable = false)
    private SupportedCurrency baseCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "quote_currency", nullable = false)
    private SupportedCurrency quoteCurrency;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public FxRate() {
    }

    public FxRate(SupportedCurrency baseCurrency, SupportedCurrency quoteCurrency, BigDecimal rate, Instant updatedAt) {
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.rate = rate;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public SupportedCurrency getBaseCurrency() {
        return baseCurrency;
    }

    public SupportedCurrency getQuoteCurrency() {
        return quoteCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
