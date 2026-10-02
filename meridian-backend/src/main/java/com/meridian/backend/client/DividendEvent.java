package com.meridian.backend.client;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A cash dividend as the provider reports it: USD per share, owed to holders when the ex-date began. */
public record DividendEvent(LocalDate exDate, LocalDate payDate, BigDecimal amount) {
}
