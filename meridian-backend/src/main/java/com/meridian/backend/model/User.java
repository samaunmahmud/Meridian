package com.meridian.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    // NEVER store a plain password. This holds a bcrypt HASH — a one-way
    // scramble of the password. Even if this database were ever leaked,
    // the original passwords cannot be recovered from the hash.
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    // Sessions issued before this moment are refused (set when the password changes).
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    public User() {
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    /** Stores the new hash and ends every session issued before `at`. */
    public void changePassword(String newPasswordHash, Instant at) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = at;
    }
}
