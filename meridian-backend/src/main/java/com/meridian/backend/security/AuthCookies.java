package com.meridian.backend.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

// The login token travels in an HttpOnly cookie. JavaScript on the page can
// never read an HttpOnly cookie, so a cross-site-scripting bug can no longer
// steal the session token the way it could from localStorage.
@Component
public class AuthCookies {

    public static final String NAME = "meridian_token";

    private final JwtUtil jwtUtil;
    // Set auth.cookie-secure=true (AUTH_COOKIE_SECURE) once the site is served over HTTPS.
    private final boolean secure;
    private final String sameSite;

    public AuthCookies(JwtUtil jwtUtil,
                       @Value("${auth.cookie-secure:false}") boolean secure,
                       @Value("${auth.cookie-same-site:Lax}") String sameSite) {
        this.jwtUtil = jwtUtil;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie create(String token) {
        return base(token).maxAge(Duration.ofSeconds(jwtUtil.lifetimeSeconds())).build();
    }

    /** An expired, empty cookie: tells the browser to delete the session. */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite(sameSite).path("/");
    }

    /** The token from the request's cookie, or null. */
    public String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
