package com.meridian.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

// A JWT (JSON Web Token) is a signed, tamper-proof string the server hands
// to the browser after login. The browser sends it back on every request
// (in the Authorization header) instead of re-sending the password each
// time. Because it's cryptographically SIGNED with a secret only the
// server knows, the server can trust its contents without hitting the
// database on every single request — it just verifies the signature.
@Component
public class JwtUtil {

    private final SecretKey key;
    private static final long EXPIRATION_MS = 1000L * 60 * 60 * 24; // 24 hours

    public JwtUtil(@Value("${JWT_SECRET}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public long lifetimeSeconds() {
        return EXPIRATION_MS / 1000;
    }

    public String generateToken(String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
                .subject(email) // the "subject" of the token — who it belongs to
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    // Returns the token's contents IF the signature and expiry are valid.
    // Throws otherwise (an expired or tampered token is rejected
    // automatically by the jjwt library).
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return parse(token).getSubject();
    }
}
