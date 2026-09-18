package com.meridian.backend.dto;

import com.meridian.backend.model.SupportedCurrency;
import java.math.BigDecimal;

public record WalletResponse(SupportedCurrency currency, BigDecimal balance) {
}
