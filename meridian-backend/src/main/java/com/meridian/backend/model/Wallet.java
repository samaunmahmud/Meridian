package com.meridian.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

// A non-USD currency balance held by a portfolio. USD itself stays on
// Portfolio.cashBalance (unchanged) — this only covers the additional
// currencies a user can hold and convert to/from USD before trading.
@Entity
@Table(
        name = "wallets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "currency"})
)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportedCurrency currency;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal balance;

    // Held back by open limit orders that pay from this wallet.
    @Column(name = "reserved_balance", nullable = false, precision = 14, scale = 4)
    private BigDecimal reservedBalance = BigDecimal.ZERO;

    public Wallet() {
    }

    public Wallet(Portfolio portfolio, SupportedCurrency currency, BigDecimal balance) {
        this.portfolio = portfolio;
        this.currency = currency;
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public SupportedCurrency getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getReservedBalance() {
        return reservedBalance;
    }

    public void setReservedBalance(BigDecimal reservedBalance) {
        this.reservedBalance = reservedBalance;
    }

    public BigDecimal getAvailableBalance() {
        return balance.subtract(reservedBalance);
    }
}
