package com.meridian.backend.dto;

import com.meridian.backend.model.AlertDirection;
import java.math.BigDecimal;

// Either targetPrice, or movePercent: "ABOVE 5" means 5% above the latest price.
public record AlertRequest(String symbol, AlertDirection direction, BigDecimal targetPrice, BigDecimal movePercent) {

    public AlertRequest(String symbol, AlertDirection direction, BigDecimal targetPrice) {
        this(symbol, direction, targetPrice, null);
    }
}
