package com.meridian.backend.dto;

import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.SupportedCurrency;

import java.math.BigDecimal;

public record OrderRequest(
        String symbol,
        OrderType type,
        OrderKind kind,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal stopPrice,
        // Wallet to pay from (buy) or be paid into (sell). Null = USD.
        // Only market orders can settle in another currency.
        SupportedCurrency settlementCurrency
) {
    // Convenience for the common USD case.
    public OrderRequest(String symbol, OrderType type, OrderKind kind, BigDecimal quantity,
                        BigDecimal limitPrice, BigDecimal stopPrice) {
        this(symbol, type, kind, quantity, limitPrice, stopPrice, null);
    }
}
