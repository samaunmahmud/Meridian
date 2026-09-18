package com.meridian.backend.dto;

import com.meridian.backend.model.AlertDirection;
import java.math.BigDecimal;

public record AlertRequest(String symbol, AlertDirection direction, BigDecimal targetPrice) {
}
