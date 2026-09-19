package com.meridian.backend.dto;

import com.meridian.backend.model.SupportedCurrency;
import java.math.BigDecimal;

// `reserved` is held back by open limit orders; `available` = balance - reserved.
public record WalletResponse(SupportedCurrency currency, BigDecimal balance, BigDecimal reserved, BigDecimal available) {
}
