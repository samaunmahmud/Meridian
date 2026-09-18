package com.meridian.backend.dto;

import com.meridian.backend.model.SupportedCurrency;
import java.math.BigDecimal;

// Shared shape for both a wallet deposit and a wallet withdrawal request.
public record WalletAmountRequest(SupportedCurrency currency, BigDecimal amount) {
}
