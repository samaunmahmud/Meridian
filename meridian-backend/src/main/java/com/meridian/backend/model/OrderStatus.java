package com.meridian.backend.model;

public enum OrderStatus {
    PENDING,
    FILLED,
    CANCELLED,
    // The system gave up on a pending order (e.g. it could no longer be
    // afforded when its price triggered). Reserved cash/shares are released.
    REJECTED
}
