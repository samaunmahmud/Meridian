package com.meridian.backend.security;

import com.meridian.backend.model.RateLimitEvent;
import com.meridian.backend.repository.RateLimitEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Database storage behind SlidingWindowLimiter. Writes run in their OWN
// transaction: a failed login or sign-up must stay counted even when the
// request that caused it rolls back (register() is @Transactional and throws
// on a duplicate email, which would otherwise erase the record of the attempt).
@Component
public class RateLimitStore {

    private final RateLimitEventRepository events;
    private final Clock clock;

    public RateLimitStore(RateLimitEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String bucket, Duration window) {
        Instant now = clock.instant();
        events.deleteExpiredInBucket(bucket, now.minus(window)); // keeps each bucket small
        events.save(new RateLimitEvent(bucket, now));
    }

    /** 0 if another event is allowed now; otherwise seconds until enough events expire to allow one. */
    @Transactional(readOnly = true)
    public long retryAfterSeconds(String bucket, int max, Duration window) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        long count = events.countByBucketAndOccurredAtAfter(bucket, cutoff);
        if (count < max) return 0;

        // The event that must expire before the count drops below `max`.
        Instant blocking = events.occurrencesAfter(bucket, cutoff, PageRequest.of((int) (count - max), 1)).get(0);
        return Math.max(1, Duration.between(now, blocking.plus(window)).toSeconds() + 1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reset(String bucket) {
        events.clearBucket(bucket);
    }

    /** Removes events too old to matter to any limiter (buckets that are never touched again). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeOlderThan(Duration age) {
        return events.deleteOlderThan(clock.instant().minus(age));
    }
}
