package com.meridian.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DividendPaidMessage(
        String kind,          // always "DIVIDEND_PAID"
        String symbol,
        BigDecimal shares,
        BigDecimal perShare,
        BigDecimal amount,    // USD credited to cash
        LocalDate payDate
) {
}
