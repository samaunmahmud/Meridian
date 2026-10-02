package com.meridian.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "watchlist_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "ticker_id"})
)
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Why you are watching it, in your own words (null: none).
    @Column(length = 500)
    private String note;

    // The price you would like to buy at (null: none).
    @Column(name = "target_price", precision = 12, scale = 4)
    private BigDecimal targetPrice;

    public WatchlistItem() {
    }

    public WatchlistItem(User user, Ticker ticker) {
        this.user = user;
        this.ticker = ticker;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public void setTargetPrice(BigDecimal targetPrice) {
        this.targetPrice = targetPrice;
    }
}
