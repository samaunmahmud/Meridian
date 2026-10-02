package com.meridian.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A stock's cash dividends (newest ex-date first, announced ones included), what it paid per share over the
 * last year, and what this account has been paid by it. Everything is in USD; the list is empty for crypto
 * and for stocks that pay none.
 */
public record DividendInfoResponse(String symbol, List<DividendResponse> dividends, BigDecimal trailingYearPerShare,
                                   BigDecimal received) {

    public record DividendResponse(LocalDate exDate, LocalDate payDate, BigDecimal amount) {
    }
}
