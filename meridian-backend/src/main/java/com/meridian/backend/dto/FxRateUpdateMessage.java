package com.meridian.backend.dto;

import com.meridian.backend.model.SupportedCurrency;
import java.math.BigDecimal;
import java.time.Instant;

public record FxRateUpdateMessage(
        String kind,          // always "FX_RATE_UPDATE"
        SupportedCurrency baseCurrency,
        SupportedCurrency quoteCurrency,
        BigDecimal rate,
        Instant updatedAt
) {
}
