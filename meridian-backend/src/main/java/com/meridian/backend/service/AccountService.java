package com.meridian.backend.service;

import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.mail.MailService;
import com.meridian.backend.model.AuthToken;
import com.meridian.backend.model.TokenPurpose;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.AuthTokenRepository;
import com.meridian.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

// Email verification and password reset. Both work the same way: a random
// token goes out in an email link, only its hash is stored, and the link works
// once until it expires.
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private static final Duration VERIFICATION_LINK_LIFETIME = Duration.ofHours(24);
    private static final Duration RESET_LINK_LIFETIME = Duration.ofHours(1);
    private static final String INVALID_LINK = "This link is invalid or has expired. Please request a new one.";

    private final UserRepository userRepository;
    private final AuthTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final TaskExecutor mailExecutor;
    private final LoginAttemptService attempts;
    private final Clock clock;
    private final String publicUrl;
    private final SecureRandom random = new SecureRandom();

    public AccountService(UserRepository userRepository, AuthTokenRepository tokenRepository,
                          PasswordEncoder passwordEncoder, MailService mailService,
                          @Qualifier("mailExecutor") TaskExecutor mailExecutor,
                          LoginAttemptService attempts, Clock clock,
                          @Value("${app.public-url}") String publicUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.mailExecutor = mailExecutor;
        this.attempts = attempts;
        this.clock = clock;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    // ------------------------------------------------------------ email verification

    @Transactional
    public void sendVerification(User user) {
        String token = issueToken(user, TokenPurpose.EMAIL_VERIFICATION, VERIFICATION_LINK_LIFETIME);
        sendEmail(user.getEmail(), "Confirm your Meridian email address",
                "Welcome to Meridian!\n\n"
                        + "Confirm your email address by opening this link (it works once and expires in 24 hours):\n\n"
                        + publicUrl + "/?verify=" + token + "\n\n"
                        + "If you didn't create a Meridian account, you can ignore this email.");
    }

    @Transactional
    public void resendVerification(User user) {
        if (user.isEmailVerified()) {
            return;
        }
        attempts.checkAndRecordVerificationResend(user.getEmail());
        sendVerification(user);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        AuthToken token = usableToken(rawToken, TokenPurpose.EMAIL_VERIFICATION);
        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        token.markUsed(clock.instant());
        tokenRepository.save(token);
    }

    // ------------------------------------------------------------ password reset

    // Always behaves the same whether or not the address has an account (the
    // caller answers with the same message), so it cannot be used to find out
    // who is registered. The email is sent in the background for the same reason.
    @Transactional
    public void requestPasswordReset(String email, String clientIp) {
        attempts.checkAndRecordPasswordReset(email, clientIp);
        User user = email == null || email.isBlank() ? null : userRepository.findByEmail(email.trim()).orElse(null);
        if (user == null) {
            return;
        }
        String token = issueToken(user, TokenPurpose.PASSWORD_RESET, RESET_LINK_LIFETIME);
        sendEmail(user.getEmail(), "Reset your Meridian password",
                "We received a request to reset the password for your Meridian account.\n\n"
                        + "Choose a new password here (the link works once and expires in 1 hour):\n\n"
                        + publicUrl + "/?reset=" + token + "\n\n"
                        + "If you didn't ask for this, ignore this email: your password stays the same.");
    }

    // Changing the password also signs out every existing session (see
    // User.changePassword) and lifts any login lockout, since the person who
    // just proved they own the mailbox is the account's owner.
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordPolicy.validate(newPassword); // checked first, so a typo doesn't burn the link
        AuthToken token = usableToken(rawToken, TokenPurpose.PASSWORD_RESET);
        User user = token.getUser();
        Instant now = clock.instant();

        user.changePassword(passwordEncoder.encode(newPassword), now);
        userRepository.save(user);
        token.markUsed(now);
        tokenRepository.save(token);
        tokenRepository.invalidateUnused(user.getId(), TokenPurpose.PASSWORD_RESET, now);
        attempts.recordSuccess(user.getEmail());

        sendEmail(user.getEmail(), "Your Meridian password was changed",
                "The password for your Meridian account was just changed, and you were signed out everywhere.\n\n"
                        + "If this was you, there's nothing more to do. If it wasn't, reset your password again right away "
                        + "using \"Forgot password\" on the sign-in page.");
    }

    // ------------------------------------------------------------ internals

    private String issueToken(User user, TokenPurpose purpose, Duration lifetime) {
        Instant now = clock.instant();
        tokenRepository.invalidateUnused(user.getId(), purpose, now); // only the newest email works
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.save(new AuthToken(user, purpose, sha256(raw), now, now.plus(lifetime)));
        return raw;
    }

    private AuthToken usableToken(String rawToken, TokenPurpose purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRequestException(INVALID_LINK);
        }
        return tokenRepository.findByTokenHashAndPurpose(sha256(rawToken.trim()), purpose)
                .filter(t -> t.isUsableAt(clock.instant()))
                .orElseThrow(() -> new InvalidRequestException(INVALID_LINK));
    }

    // Sent after the surrounding transaction commits (so the link never
    // arrives before its token exists, or for a request that rolled back), and
    // off the request thread. A failure to send is logged, not shown to the user.
    private void sendEmail(String to, String subject, String body) {
        Runnable send = () -> mailExecutor.execute(() -> {
            try {
                mailService.send(to, subject, body);
            } catch (Exception e) {
                log.warn("Could not send \"{}\" email", subject, e);
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
