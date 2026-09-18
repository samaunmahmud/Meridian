package com.meridian.backend.dto;

import com.meridian.backend.model.RecurringFrequency;
import java.math.BigDecimal;

public record RecurringOrderRequest(String symbol, BigDecimal amount, RecurringFrequency frequency) {
}
