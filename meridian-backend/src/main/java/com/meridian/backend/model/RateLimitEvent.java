package com.meridian.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

// One counted event (a failed login, a sign-up) for a rate limiter.
// `bucket` identifies the limiter and the email/IP it counts, hashed.
@Entity
@Table(
        name = "rate_limit_events",
        indexes = @Index(name = "idx_rate_limit_bucket_time", columnList = "bucket, occurred_at")
)
public class RateLimitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String bucket;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public RateLimitEvent() {
    }

    public RateLimitEvent(String bucket, Instant occurredAt) {
        this.bucket = bucket;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getBucket() {
        return bucket;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
