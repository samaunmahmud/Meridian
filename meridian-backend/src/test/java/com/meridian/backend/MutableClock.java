package com.meridian.backend;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** A clock the test moves by hand. */
public class MutableClock extends Clock {
    private Instant now;
    private final ZoneId zone;

    public MutableClock(Instant start) {
        this(start, ZoneId.of("UTC"));
    }

    private MutableClock(Instant start, ZoneId zone) {
        this.now = start;
        this.zone = zone;
    }

    public void advance(Duration d) {
        now = now.plus(d);
    }

    public void set(Instant t) {
        now = t;
    }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId z) { return new MutableClock(now, z) { @Override public Instant instant() { return MutableClock.this.now; } }; }
    @Override public Instant instant() { return now; }
}
