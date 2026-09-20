package com.meridian.backend.security;

import com.meridian.backend.model.User;
import com.meridian.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import java.time.Instant;
import java.util.Optional;

// Decides whether a session token still counts. Beyond a valid signature and
// expiry, a token issued before the user last changed their password is
// refused, so a password reset signs out everyone who had the old one.
// Shared by the HTTP filter and the websocket handshake.
@Component
public class SessionAuthenticator {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public SessionAuthenticator(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    // Empty means "this is not a valid session". If the DATABASE cannot be reached the answer is
    // unknown, so that is thrown (DataAccessException / TransactionException) instead of being
    // reported as an invalid session: otherwise a database outage looks like "your session has
    // ended" and signs every user out in the browser.
    public Optional<User> authenticate(String token) {
        try {
            Claims claims = jwtUtil.parse(token);
            User user = userRepository.findByEmail(claims.getSubject()).orElse(null);
            if (user == null) {
                return Optional.empty();
            }
            Instant changed = user.getPasswordChangedAt();
            // JWT issue times are whole seconds, so compare in seconds: a session
            // created right after the change (same second) stays valid.
            if (changed != null && claims.getIssuedAt().toInstant().getEpochSecond() < changed.getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(user);
        } catch (DataAccessException | TransactionException e) {
            throw e;
        } catch (Exception e) {
            return Optional.empty(); // bad signature, expired, malformed
        }
    }
}
