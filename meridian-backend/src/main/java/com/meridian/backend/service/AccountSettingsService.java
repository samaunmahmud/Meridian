package com.meridian.backend.service;

import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.mail.MailService;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.UserRepository;
import com.meridian.backend.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

// What a signed-in user can do to their own account: change the password and
// delete the account. Both ask for the current password again, so a session
// left open on a shared computer is not enough. Wrong guesses count towards
// the same lockout as failed logins.
@Service
public class AccountSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AccountSettingsService.class);

    // Children before parents, so no foreign key is ever left pointing at a deleted row.
    private static final String[] PORTFOLIO_TABLES = {
            "holdings", "orders", "transactions", "portfolio_snapshots", "recurring_orders", "wallets"
    };
    private static final String[] USER_TABLES = {"auth_tokens", "alerts", "watchlist_items"};

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService attempts;
    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbc;
    private final MailService mailService;
    private final TaskExecutor mailExecutor;
    private final Clock clock;

    public AccountSettingsService(UserRepository userRepository, PortfolioRepository portfolioRepository,
                                  PasswordEncoder passwordEncoder, LoginAttemptService attempts, JwtUtil jwtUtil,
                                  JdbcTemplate jdbc, MailService mailService,
                                  @Qualifier("mailExecutor") TaskExecutor mailExecutor, Clock clock) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.passwordEncoder = passwordEncoder;
        this.attempts = attempts;
        this.jwtUtil = jwtUtil;
        this.jdbc = jdbc;
        this.mailService = mailService;
        this.mailExecutor = mailExecutor;
        this.clock = clock;
    }

    /**
     * Sets a new password and ends every other session. Returns a fresh session
     * token for the caller, so changing the password doesn't sign them out here.
     */
    @Transactional
    public String changePassword(User user, String currentPassword, String newPassword, String clientIp) {
        checkPassword(user, currentPassword, clientIp);
        PasswordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new InvalidRequestException("The new password must be different from the current one");
        }
        Instant now = clock.instant();
        user.changePassword(passwordEncoder.encode(newPassword), now);
        userRepository.save(user);
        notify(user.getEmail(), "Your Meridian password was changed",
                "The password for your Meridian account was just changed in Settings, and every other "
                        + "session was signed out.\n\nIf this wasn't you, reset your password right away "
                        + "using \"Forgot password\" on the sign-in page.");
        return jwtUtil.generateToken(user.getEmail());
    }

    /** Deletes the user and everything they own. There is no undo. */
    @Transactional
    public void deleteAccount(User user, String password, String clientIp) {
        checkPassword(user, password, clientIp);
        // Lock the portfolio first so a pending order can't fill (or a recurring
        // buy run) against it half-way through the deletion.
        portfolioRepository.findByUserIdForUpdate(user.getId()).ifPresent(portfolio -> {
            for (String table : PORTFOLIO_TABLES) {
                jdbc.update("delete from " + table + " where portfolio_id = ?", portfolio.getId());
            }
            jdbc.update("delete from portfolio where id = ?", portfolio.getId());
        });
        for (String table : USER_TABLES) {
            jdbc.update("delete from " + table + " where user_id = ?", user.getId());
        }
        jdbc.update("delete from users where id = ?", user.getId());
        attempts.recordSuccess(user.getEmail());
        notify(user.getEmail(), "Your Meridian account was deleted",
                "Your Meridian account and all of its data (portfolio, orders, watchlist and alerts) "
                        + "were deleted at your request.\n\nYou're welcome back any time.");
    }

    private void checkPassword(User user, String password, String clientIp) {
        attempts.checkLoginAllowed(user.getEmail(), clientIp);
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            attempts.recordFailure(user.getEmail(), clientIp);
            // 400 rather than 401: the session is fine, only the typed password is wrong.
            throw new InvalidRequestException("Your current password is incorrect");
        }
    }

    // Sent once the change has committed, off the request thread.
    private void notify(String to, String subject, String body) {
        AfterCommit.run(() -> mailExecutor.execute(() -> {
            try {
                mailService.send(to, subject, body);
            } catch (Exception e) {
                log.warn("Could not send \"{}\" email", subject, e);
            }
        }));
    }
}
