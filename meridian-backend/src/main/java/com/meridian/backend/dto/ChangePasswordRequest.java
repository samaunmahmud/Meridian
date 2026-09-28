package com.meridian.backend.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
