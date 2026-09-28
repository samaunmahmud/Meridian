package com.meridian.backend.model;

public enum OrderKind {
    MARKET,
    LIMIT,
    STOP_LOSS,
    // A sell whose stop price trails the highest price since it was placed by trailPercent.
    TRAILING_STOP
}
