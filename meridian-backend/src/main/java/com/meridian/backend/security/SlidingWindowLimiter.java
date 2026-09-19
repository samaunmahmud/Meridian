package com.meridian.backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

// Counts events per key over a rolling time window: "at most `max` events in
// the last `window`". In memory, so limits are per server instance — running
// several instances would need a shared store such as Redis.
public class SlidingWindowLimiter {

    private static final int SWEEP_ABOVE_KEYS = 10_000;

    private final int max;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(int max, Duration window, Clock clock) {
        this.max = max;
        this.window = window;
        this.clock = clock;
    }

    public void record(String key) {
        Instant now = clock.instant();
        events.compute(key, (k, deque) -> {
            Deque<Instant> d = deque == null ? new ArrayDeque<>() : deque;
            prune(d, now);
            d.addLast(now);
            return d;
        });
        if (events.size() > SWEEP_ABOVE_KEYS) {
            sweep(now);
        }
    }

    /** 0 if another event is allowed now; otherwise seconds until the oldest counted event expires. */
    public long retryAfterSeconds(String key) {
        Instant now = clock.instant();
        long[] result = {0};
        events.computeIfPresent(key, (k, d) -> {
            prune(d, now);
            if (d.size() >= max) {
                long seconds = Duration.between(now, d.peekFirst().plus(window)).toSeconds() + 1;
                result[0] = Math.max(1, seconds);
            }
            return d.isEmpty() ? null : d;
        });
        return result[0];
    }

    public void reset(String key) {
        events.remove(key);
    }

    private void prune(Deque<Instant> d, Instant now) {
        Instant cutoff = now.minus(window);
        while (!d.isEmpty() && !d.peekFirst().isAfter(cutoff)) {
            d.removeFirst();
        }
    }

    private void sweep(Instant now) {
        events.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }
}
