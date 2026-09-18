package com.meridian.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 14, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Which currency `amount` is denominated in — nullable so existing
    // DEPOSIT/WITHDRAWAL rows (implicitly USD) don't need a backfill.
    @Column
    private String currency;

    // Free-text detail for the activity feed, e.g. "Bought 2 AAPL".
    @Column
    private String description;

    // Links a BUY/SELL/FEE row back to the order that produced it.
    @Column(name = "related_order_id")
    private Long relatedOrderId;

    public Transaction() {
    }

    public Transaction(Portfolio portfolio, TransactionType type, BigDecimal amount, BigDecimal balanceAfter) {
        this.portfolio = portfolio;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = Instant.now();
    }

    public Transaction(Portfolio portfolio, TransactionType type, BigDecimal amount, BigDecimal balanceAfter,
                        String currency, String description, Long relatedOrderId) {
        this(portfolio, type, amount, balanceAfter);
        this.currency = currency;
        this.description = description;
        this.relatedOrderId = relatedOrderId;
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public Long getRelatedOrderId() {
        return relatedOrderId;
    }
}
