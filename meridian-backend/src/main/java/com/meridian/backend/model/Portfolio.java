package com.meridian.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

// Each User has exactly one Portfolio — @OneToOne with a unique user_id
// enforces that at the database level (a second portfolio for the same
// user is simply impossible to insert).
@Entity
@Table(name = "portfolio")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "cash_balance", nullable = false, precision = 14, scale = 4)
    private BigDecimal cashBalance;

    // Cash locked up by open BUY limit orders — held out of cashBalance so
    // it can't be spent twice (once by the pending order, once by a new one).
    @Column(name = "reserved_cash", nullable = false, precision = 14, scale = 4)
    private BigDecimal reservedCash = BigDecimal.ZERO;

    public Portfolio() {
    }

    public Portfolio(User user, BigDecimal cashBalance) {
        this.user = user;
        this.cashBalance = cashBalance;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public BigDecimal getReservedCash() {
        return reservedCash;
    }

    public void setReservedCash(BigDecimal reservedCash) {
        this.reservedCash = reservedCash;
    }

    public BigDecimal getAvailableCash() {
        return cashBalance.subtract(reservedCash);
    }
}
