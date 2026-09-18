package com.meridian.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

// A standing instruction to buy a fixed USD amount of a ticker on a
// schedule — the RecurringOrderScheduler places a normal MARKET order for
// whatever quantity that amount buys each time nextRunAt is reached.
@Entity
@Table(name = "recurring_orders")
public class RecurringOrder {

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
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringFrequency frequency;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RecurringOrder() {
    }

    public RecurringOrder(Portfolio portfolio, Ticker ticker, BigDecimal amount, RecurringFrequency frequency, Instant nextRunAt) {
        this.portfolio = portfolio;
        this.ticker = ticker;
        this.amount = amount;
        this.frequency = frequency;
        this.nextRunAt = nextRunAt;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public void advanceNextRun() {
        this.nextRunAt = switch (frequency) {
            case DAILY -> nextRunAt.plus(1, ChronoUnit.DAYS);
            case WEEKLY -> nextRunAt.plus(7, ChronoUnit.DAYS);
            case MONTHLY -> nextRunAt.plus(30, ChronoUnit.DAYS);
        };
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

    public BigDecimal getAmount() {
        return amount;
    }

    public RecurringFrequency getFrequency() {
        return frequency;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
