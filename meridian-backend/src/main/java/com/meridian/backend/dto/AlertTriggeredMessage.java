package com.meridian.backend.dto;

import com.meridian.backend.model.AlertDirection;
import java.math.BigDecimal;
import java.time.Instant;

public record AlertTriggeredMessage(
        String kind,          // always "ALERT_TRIGGERED" — lets the frontend tell this apart from a plain price update
        Long alertId,
        String symbol,
        AlertDirection direction,
        BigDecimal targetPrice,
        BigDecimal triggeredPrice,
        Instant triggeredAt
) {
}
