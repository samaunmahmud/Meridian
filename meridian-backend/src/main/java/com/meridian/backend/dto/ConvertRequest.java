package com.meridian.backend.dto;

import com.meridian.backend.model.SupportedCurrency;
import java.math.BigDecimal;

public record ConvertRequest(SupportedCurrency fromCurrency, SupportedCurrency toCurrency, BigDecimal amount) {
}
