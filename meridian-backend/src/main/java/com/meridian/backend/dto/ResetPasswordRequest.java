package com.meridian.backend.dto;

public record ResetPasswordRequest(String token, String password) {
}
