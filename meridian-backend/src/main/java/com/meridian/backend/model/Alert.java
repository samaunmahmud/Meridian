package com.meridian.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertDirection direction;

    @Column(name = "target_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal targetPrice;

    @Column(nullable = false)
    private boolean triggered = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    public Alert() {
    }

    public Alert(User user, Ticker ticker, AlertDirection direction, BigDecimal targetPrice) {
        this.user = user;
        this.ticker = ticker;
        this.direction = direction;
        this.targetPrice = targetPrice;
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

    public AlertDirection getDirection() {
        return direction;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public void setTriggered(boolean triggered) {
        this.triggered = triggered;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }
}
