package com.meridian.backend.dto;

import com.meridian.backend.model.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant createdAt
) {
}
