package com.meridian.backend;

import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.AuthTokenRepository;
import com.meridian.backend.scheduler.AuthTokenCleanupScheduler;
import com.meridian.backend.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** How long email links last: a reset link one hour, a verification link a day. */
class AccountLinkExpiryTest extends IntegrationTestBase {

    @TestConfiguration
    static class FakeClockAndMail {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now());
        }

        @Bean
        @Primary
        RecordingMailService recordingMailService() {
            return new RecordingMailService();
        }
    }

    @Autowired MutableClock clock;
    @Autowired RecordingMailService mail;
    @Autowired AccountService accounts;
    @Autowired AuthTokenRepository tokens;
    @Autowired PlatformTransactionManager transactions;

    private String requestReset(User user) {
        accounts.requestPasswordReset(user.getEmail(), "10.99.0.1");
        return mail.lastToken(user.getEmail(), "reset");
    }

    @Test
    void aResetLinkWorksForAnHourAndNotAfter() {
        User user = newUser("0.00");
        String token = requestReset(user);
        clock.advance(Duration.ofMinutes(59));
        accounts.resetPassword(token, "long-enough-password"); // still fine

        String late = requestReset(user);
        clock.advance(Duration.ofMinutes(61));
        assertThatThrownBy(() -> accounts.resetPassword(late, "another-long-password"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("invalid or has expired");
    }

    @Test
    void aVerificationLinkWorksForADayAndNotAfter() {
        User user = newUser("0.00");
        accounts.sendVerification(user);
        String token = mail.lastToken(user.getEmail(), "verify");

        clock.advance(Duration.ofHours(23));
        accounts.verifyEmail(token); // still fine
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();

        User other = newUser("0.00");
        accounts.sendVerification(other);
        String late = mail.lastToken(other.getEmail(), "verify");
        clock.advance(Duration.ofHours(25));
        assertThatThrownBy(() -> accounts.verifyEmail(late)).isInstanceOf(InvalidRequestException.class);
        assertThat(userRepository.findById(other.getId()).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void theCleanupJobRemovesLinksExpiredForMoreThanAWeekAndNothingNewer() {
        User user = newUser("0.00");
        accounts.sendVerification(user);                 // expires in 1 day
        long before = tokens.count();

        clock.advance(Duration.ofDays(5));               // expired 4 days ago: kept
        new TransactionTemplate(transactions).executeWithoutResult(s -> new AuthTokenCleanupScheduler(tokens, clock).purgeExpiredTokens());
        assertThat(tokens.count()).isEqualTo(before);

        clock.advance(Duration.ofDays(4));               // expired 8 days ago: removed
        new TransactionTemplate(transactions).executeWithoutResult(s -> new AuthTokenCleanupScheduler(tokens, clock).purgeExpiredTokens());
        assertThat(tokens.count()).isLessThan(before);
    }
}
