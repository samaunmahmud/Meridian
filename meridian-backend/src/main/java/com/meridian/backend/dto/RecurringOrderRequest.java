package com.meridian.backend.dto;

import com.meridian.backend.model.RecurringFrequency;
import com.meridian.backend.model.SupportedCurrency;
import java.math.BigDecimal;

// `amount` is in `settlementCurrency` (null = USD): the value of the shares
// bought each time, with commission and any exchange spread charged on top.
public record RecurringOrderRequest(String symbol, BigDecimal amount, RecurringFrequency frequency,
                                    SupportedCurrency settlementCurrency) {

    public RecurringOrderRequest(String symbol, BigDecimal amount, RecurringFrequency frequency) {
        this(symbol, amount, frequency, null);
    }
}
