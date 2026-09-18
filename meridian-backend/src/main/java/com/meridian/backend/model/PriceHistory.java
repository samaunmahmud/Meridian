package com.meridian.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "price_history",
    indexes = @Index(name = "idx_ticker_recorded_at", columnList = "ticker_id, recorded_at")
)
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal price;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public PriceHistory() {
    }

    public PriceHistory(Ticker ticker, BigDecimal price, Instant recordedAt) {
        this.ticker = ticker;
        this.price = price;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public void setTicker(Ticker ticker) {
        this.ticker = ticker;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
