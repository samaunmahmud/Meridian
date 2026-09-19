package com.meridian.backend.service;

import com.meridian.backend.exception.InvalidRequestException;

import java.nio.charset.StandardCharsets;

// The rules for a new password, shared by sign-up and password reset.
final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_BYTES = 72; // bcrypt ignores everything after 72 bytes

    private PasswordPolicy() {
    }

    static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new InvalidRequestException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidRequestException("Password is too long (at most " + MAX_BYTES + " bytes)");
        }
    }
}
