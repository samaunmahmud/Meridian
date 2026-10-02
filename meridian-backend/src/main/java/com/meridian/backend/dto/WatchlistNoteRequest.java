package com.meridian.backend.dto;

import java.math.BigDecimal;

/** Your note on a stock and the price you'd like to buy at; null or blank clears either. */
public record WatchlistNoteRequest(String note, BigDecimal targetPrice) {
}
