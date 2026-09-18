package com.meridian.backend.dto;

import com.meridian.backend.model.SupportedCurrency;
import java.math.BigDecimal;

public record ConvertResponse(
        SupportedCurrency fromCurrency,
        SupportedCurrency toCurrency,
        BigDecimal amountDebited,
        BigDecimal amountCredited,
        BigDecimal rateApplied,
        BigDecimal fee
) {
}
