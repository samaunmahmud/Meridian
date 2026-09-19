package com.meridian.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

// A single-use link sent by email (verify an address, reset a password).
// Only the hash of the token is stored; the token itself exists only in the email.
@Entity
@Table(name = "auth_tokens")
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AuthToken() {
    }

    public AuthToken(User user, TokenPurpose purpose, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.user = user;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public User getUser() {
        return user;
    }

    public TokenPurpose getPurpose() {
        return purpose;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void markUsed(Instant at) {
        this.usedAt = at;
    }

    public boolean isUsableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
