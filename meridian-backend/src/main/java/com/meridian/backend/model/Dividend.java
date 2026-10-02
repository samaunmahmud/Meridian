package com.meridian.backend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// A cash dividend a stock pays: `amount` USD per share to whoever held it when the
// ex-dividend date began, paid on `payDate`. `paidOutAt` is set once every holder is paid.
@Entity
@Table(
        name = "dividends",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ticker_id", "ex_date"})
)
public class Dividend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Column(name = "ex_date", nullable = false)
    private LocalDate exDate;

    @Column(name = "pay_date", nullable = false)
    private LocalDate payDate;

    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal amount;

    @Column(name = "paid_out_at")
    private Instant paidOutAt;

    public Dividend() {
    }

    public Dividend(Ticker ticker, LocalDate exDate, LocalDate payDate, BigDecimal amount) {
        this.ticker = ticker;
        this.exDate = exDate;
        this.payDate = payDate;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public LocalDate getExDate() {
        return exDate;
    }

    public LocalDate getPayDate() {
        return payDate;
    }

    public void setPayDate(LocalDate payDate) {
        this.payDate = payDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getPaidOutAt() {
        return paidOutAt;
    }

    public void setPaidOutAt(Instant paidOutAt) {
        this.paidOutAt = paidOutAt;
    }
}
