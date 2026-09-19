package com.meridian.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

// "At most `max` events per key in the last `window`", counted in the
// database (see RateLimitStore) so the limit is shared by every server
// instance and survives a restart. `name` keeps different limiters that use
// the same key (an email, an IP) from counting each other's events.
public class SlidingWindowLimiter {

    private final RateLimitStore store;
    private final String name;
    private final int max;
    private final Duration window;

    public SlidingWindowLimiter(RateLimitStore store, String name, int max, Duration window) {
        this.store = store;
        this.name = name;
        this.max = max;
        this.window = window;
    }

    public void record(String key) {
        store.record(bucket(key), window);
    }

    /** 0 if another event is allowed now; otherwise seconds until one will be. */
    public long retryAfterSeconds(String key) {
        return store.retryAfterSeconds(bucket(key), max, window);
    }

    public void reset(String key) {
        store.reset(bucket(key));
    }

    // Hashed so raw emails and IPs are not duplicated into the events table.
    private String bucket(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((name + '\0' + key).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 is required on every JVM
        }
    }
}
