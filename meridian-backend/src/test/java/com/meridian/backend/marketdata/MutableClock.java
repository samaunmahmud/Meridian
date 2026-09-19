package com.meridian.backend.marketdata;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** A clock the test moves by hand. */
class MutableClock extends Clock {
    private Instant now;
    private final ZoneId zone;

    MutableClock(Instant start) {
        this(start, ZoneId.of("UTC"));
    }

    private MutableClock(Instant start, ZoneId zone) {
        this.now = start;
        this.zone = zone;
    }

    void advance(Duration d) {
        now = now.plus(d);
    }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId z) { return new MutableClock(now, z) { @Override public Instant instant() { return MutableClock.this.now; } }; }
    @Override public Instant instant() { return now; }
}
