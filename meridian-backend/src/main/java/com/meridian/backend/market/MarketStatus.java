package com.meridian.backend.market;

import java.time.Instant;

/**
 * Whether a market accepts trades right now, and when that next changes.
 * {@code nextOpen} is set while closed, {@code nextClose} while open; crypto has neither.
 */
public record MarketStatus(boolean open, Instant nextOpen, Instant nextClose) {

    public static MarketStatus alwaysOpen() {
        return new MarketStatus(true, null, null);
    }
}
