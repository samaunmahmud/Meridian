package com.meridian.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Links every order back to the portfolio (and therefore user) that
    // placed it — this is what makes per-user order history possible.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticker_id", nullable = false)
    private Ticker ticker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    // Fill price — null until a MARKET order executes immediately or a
    // pending LIMIT/STOP_LOSS order is matched by the scheduler.
    @Column(precision = 14, scale = 4)
    private BigDecimal price;

    @Column(name = "limit_price", precision = 14, scale = 4)
    private BigDecimal limitPrice;

    @Column(name = "stop_price", precision = 14, scale = 4)
    private BigDecimal stopPrice;

    // Commission charged when this order fills — null until FILLED.
    @Column(name = "fee_amount", precision = 14, scale = 4)
    private BigDecimal feeAmount;

    // Cash held back for a pending BUY: price * quantity PLUS the commission
    // it will cost at the limit price. Stored so cancel/fill release exactly
    // what was held. Null for orders created before this column existed.
    @Column(name = "reserved_amount", precision = 14, scale = 4)
    private BigDecimal reservedAmount;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    // Wallet the trade was paid from / credited to (market orders can settle
    // in EUR or GBP). Null on orders that settled in USD before this existed.
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_currency")
    private SupportedCurrency settlementCurrency;

    // What actually left (buy) or reached (sell) that wallet, in its currency.
    @Column(name = "settlement_amount", precision = 14, scale = 4)
    private BigDecimal settlementAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public Order() {
    }

    // MARKET order — fills immediately, so it's created already FILLED.
    public Order(Portfolio portfolio, Ticker ticker, OrderType type, BigDecimal quantity, BigDecimal price, Instant executedAt) {
        this.portfolio = portfolio;
        this.ticker = ticker;
        this.type = type;
        this.kind = OrderKind.MARKET;
        this.status = OrderStatus.FILLED;
        this.quantity = quantity;
        this.price = price;
        this.createdAt = executedAt;
        this.executedAt = executedAt;
    }

    // LIMIT / STOP_LOSS order — created PENDING, with no fill price yet.
    public Order(Portfolio portfolio, Ticker ticker, OrderType type, OrderKind kind, BigDecimal quantity,
                 BigDecimal limitPrice, BigDecimal stopPrice, Instant createdAt) {
        this.portfolio = portfolio;
        this.ticker = ticker;
        this.type = type;
        this.kind = kind;
        this.status = OrderStatus.PENDING;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.stopPrice = stopPrice;
        this.createdAt = createdAt;
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

    public OrderType getType() {
        return type;
    }

    public OrderKind getKind() {
        return kind;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public BigDecimal getStopPrice() {
        return stopPrice;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public BigDecimal getReservedAmount() {
        return reservedAmount;
    }

    public void setReservedAmount(BigDecimal reservedAmount) {
        this.reservedAmount = reservedAmount;
    }

    public SupportedCurrency getSettlementCurrency() {
        return settlementCurrency;
    }

    public void setSettlementCurrency(SupportedCurrency settlementCurrency) {
        this.settlementCurrency = settlementCurrency;
    }

    public BigDecimal getSettlementAmount() {
        return settlementAmount;
    }

    public void setSettlementAmount(BigDecimal settlementAmount) {
        this.settlementAmount = settlementAmount;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}
