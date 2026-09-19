package com.meridian.backend.dto;

// The session token is NOT part of the body: it is set as an HttpOnly cookie,
// which page scripts cannot read.
public record AuthResponse(String email) {
}
