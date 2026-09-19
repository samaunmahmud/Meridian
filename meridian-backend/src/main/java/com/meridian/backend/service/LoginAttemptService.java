package com.meridian.backend.service;

import com.meridian.backend.exception.TooManyRequestsException;
import com.meridian.backend.security.RateLimitStore;
import com.meridian.backend.security.SlidingWindowLimiter;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Slows down password guessing and account spam:
//  - 5 failed logins for one email in 15 minutes locks that email out for
//    the rest of the window (even the correct password is refused, which is
//    what stops an attacker from simply trying again)
//  - 20 failed logins from one IP in 15 minutes locks that IP out
//  - 10 registrations from one IP per hour
// The counts live in the database, so they are shared by every server
// instance and survive restarts. A successful login clears that email's
// failures. The client IP is the direct connection address; if you put a
// reverse proxy in front, configure it to pass the real address through
// (server.forward-headers-strategy).
@Component
public class LoginAttemptService {

    private final SlidingWindowLimiter accountFailures;
    private final SlidingWindowLimiter ipFailures;
    private final SlidingWindowLimiter registrations;

    public LoginAttemptService(RateLimitStore store) {
        this.accountFailures = new SlidingWindowLimiter(store, "login-account", 5, Duration.ofMinutes(15));
        this.ipFailures = new SlidingWindowLimiter(store, "login-ip", 20, Duration.ofMinutes(15));
        this.registrations = new SlidingWindowLimiter(store, "registration-ip", 10, Duration.ofHours(1));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    public void checkLoginAllowed(String email, String ip) {
        long wait = Math.max(accountFailures.retryAfterSeconds(key(email)), ipFailures.retryAfterSeconds(ip));
        if (wait > 0) {
            throw new TooManyRequestsException(
                    "Too many failed login attempts. Please try again in " + humanize(wait) + ".", wait);
        }
    }

    public void recordFailure(String email, String ip) {
        accountFailures.record(key(email));
        ipFailures.record(ip);
    }

    public void recordSuccess(String email) {
        accountFailures.reset(key(email));
    }

    public void checkAndRecordRegistration(String ip) {
        long wait = registrations.retryAfterSeconds(ip);
        if (wait > 0) {
            throw new TooManyRequestsException(
                    "Too many sign-ups from this connection. Please try again in " + humanize(wait) + ".", wait);
        }
        registrations.record(ip);
    }

    private static String humanize(long seconds) {
        return seconds >= 120 ? (seconds / 60) + " minutes" : seconds + " seconds";
    }
}
