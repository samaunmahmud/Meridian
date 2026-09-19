package com.meridian.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.backend.model.TokenPurpose;
import com.meridian.backend.repository.AuthTokenRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Password reset and email verification, end to end over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountRecoveryTest {

    @TestConfiguration
    static class Mail {
        @Bean
        @Primary
        RecordingMailService recordingMailService() {
            return new RecordingMailService();
        }
    }

    private static final AtomicInteger NEXT_IP = new AtomicInteger();
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RecordingMailService mail;
    @Autowired AuthTokenRepository tokens;

    private static String newIp() {
        int n = NEXT_IP.incrementAndGet();
        return "10.30." + (n / 250) + "." + (n % 250 + 1);
    }

    private static String newEmail() {
        return UUID.randomUUID() + "@test.io";
    }

    private MockHttpServletRequestBuilder post(String url, String ip, Object body) throws Exception {
        return MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .header("X-Requested-With", "meridian")
                .with(r -> { r.setRemoteAddr(ip); return r; });
    }

    private MvcResult register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register", newIp(), Map.of("email", email, "password", PASSWORD))).andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login", newIp(), Map.of("email", email, "password", password))).andReturn();
    }

    private int forgot(String email, String ip) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password", ip, Map.of("email", email))).andReturn().getResponse().getStatus();
    }

    private MvcResult reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/reset-password", newIp(), Map.of("token", token == null ? "" : token, "password", password))).andReturn();
    }

    private int me(Cookie session) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").cookie(session)).andReturn().getResponse().getStatus();
    }

    private static Cookie sessionOf(MvcResult result) {
        return MockCookie.parse(result.getResponse().getHeader("Set-Cookie"));
    }

    // ---------------------------------------------------------------- password reset

    @Test
    void aResetLinkSetsANewPasswordOnceAndTheOldPasswordStopsWorking() throws Exception {
        String email = newEmail();
        register(email);

        assertThat(forgot(email, newIp())).isEqualTo(200);
        String token = mail.lastToken(email, "reset");
        assertThat(token).isNotBlank();
        assertThat(mail.lastTo(email).subject()).contains("Reset your Meridian password");

        assertThat(reset(token, "brand-new-password-1").getResponse().getStatus()).isEqualTo(200);

        assertThat(login(email, "brand-new-password-1").getResponse().getStatus()).isEqualTo(200);
        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(reset(token, "another-password-2").getResponse().getStatus()).isEqualTo(400); // the link works once
        assertThat(mail.lastTo(email).subject()).contains("password was changed");
    }

    @Test
    void asksForAnUnknownAddressLookIdenticalAndSendNothing() throws Exception {
        String known = newEmail();
        register(known);
        String unknown = newEmail();

        MvcResult a = mvc.perform(post("/api/auth/forgot-password", newIp(), Map.of("email", known))).andReturn();
        MvcResult b = mvc.perform(post("/api/auth/forgot-password", newIp(), Map.of("email", unknown))).andReturn();

        assertThat(a.getResponse().getStatus()).isEqualTo(b.getResponse().getStatus()).isEqualTo(200);
        assertThat(a.getResponse().getContentAsString()).isEqualTo(b.getResponse().getContentAsString());
        assertThat(mail.sentTo(unknown)).isEmpty();
        assertThat(mail.sentTo(known)).isNotEmpty();
    }

    @Test
    void theTokenIsNeverStoredOnlyItsHash() throws Exception {
        String email = newEmail();
        register(email);
        forgot(email, newIp());
        String token = mail.lastToken(email, "reset");

        // What is stored is the SHA-256 of the token, so the raw value finds nothing...
        assertThat(tokens.findByTokenHashAndPurpose(token, TokenPurpose.PASSWORD_RESET)).isEmpty();
        // ...and the hash of it finds the record.
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        assertThat(tokens.findByTokenHashAndPurpose(hash, TokenPurpose.PASSWORD_RESET)).isPresent();
    }

    @Test
    void resettingThePasswordSignsOutEverySessionThatHadTheOldOne() throws Exception {
        String email = newEmail();
        Cookie oldSession = sessionOf(register(email));
        assertThat(me(oldSession)).isEqualTo(200);

        forgot(email, newIp());
        String token = mail.lastToken(email, "reset");
        Thread.sleep(1100); // session times are whole seconds: make sure the change lands in a later one
        reset(token, "brand-new-password-1");

        assertThat(me(oldSession)).isEqualTo(401);
        Cookie newSession = sessionOf(login(email, "brand-new-password-1"));
        assertThat(me(newSession)).isEqualTo(200);
    }

    @Test
    void aSuccessfulResetAlsoLiftsALoginLockout() throws Exception {
        String email = newEmail();
        register(email);
        for (int i = 0; i < 5; i++) {
            login(email, "wrong-password-" + i);
        }
        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(429); // locked out

        forgot(email, newIp());
        reset(mail.lastToken(email, "reset"), "brand-new-password-1");

        assertThat(login(email, "brand-new-password-1").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void aTooShortNewPasswordIsRefusedWithoutUsingUpTheLink() throws Exception {
        String email = newEmail();
        register(email);
        forgot(email, newIp());
        String token = mail.lastToken(email, "reset");

        assertThat(reset(token, "short").getResponse().getStatus()).isEqualTo(400);

        assertThat(reset(token, "long-enough-password").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void invalidOrMissingLinksAreRefused() throws Exception {
        assertThat(reset("not-a-real-token", "long-enough-password").getResponse().getStatus()).isEqualTo(400);
        assertThat(reset(null, "long-enough-password").getResponse().getStatus()).isEqualTo(400);
        // a verification link cannot be used to reset a password
        String email = newEmail();
        register(email);
        assertThat(reset(mail.lastToken(email, "verify"), "long-enough-password").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void askingAgainReplacesTheEarlierLink() throws Exception {
        String email = newEmail();
        register(email);
        forgot(email, newIp());
        String first = mail.lastToken(email, "reset");
        forgot(email, newIp());
        String second = mail.lastToken(email, "reset");

        assertThat(second).isNotEqualTo(first);
        assertThat(reset(first, "long-enough-password").getResponse().getStatus()).isEqualTo(400);
        assertThat(reset(second, "long-enough-password").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void resetRequestsAreRateLimitedPerAddress() throws Exception {
        String email = newEmail(); // the limit applies whether or not the address has an account
        for (int i = 0; i < 5; i++) {
            assertThat(forgot(email, newIp())).isEqualTo(200);
        }
        assertThat(forgot(email, newIp())).isEqualTo(429);
    }

    @Test
    void aBrokenMailServerDoesNotBreakOrRevealAnything() throws Exception {
        String email = newEmail();
        register(email);
        mail.failFromNowOn(true);
        try {
            assertThat(forgot(email, newIp())).isEqualTo(200);
        } finally {
            mail.failFromNowOn(false);
        }
    }

    // ---------------------------------------------------------------- email verification

    @Test
    void signingUpSendsAVerificationLinkThatConfirmsTheAddressOnce() throws Exception {
        String email = newEmail();
        MvcResult registered = register(email);
        Cookie session = sessionOf(registered);
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").cookie(session)).andExpect(jsonPath("$.emailVerified").value(false));
        String token = mail.lastToken(email, "verify");
        assertThat(mail.lastTo(email).subject()).contains("Confirm your Meridian email");

        mvc.perform(post("/api/auth/verify-email", newIp(), Map.of("token", token))).andExpect(status().isOk());

        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").cookie(session)).andExpect(jsonPath("$.emailVerified").value(true));
        mvc.perform(post("/api/auth/verify-email", newIp(), Map.of("token", token))).andExpect(status().isBadRequest());
    }

    @Test
    void resendingNeedsASessionIsLimitedAndSkippedOnceVerified() throws Exception {
        String email = newEmail();
        Cookie session = sessionOf(register(email));
        mvc.perform(post("/api/auth/resend-verification", newIp(), Map.of())).andExpect(status().isUnauthorized());

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/resend-verification", newIp(), Map.of()).cookie(session)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/auth/resend-verification", newIp(), Map.of()).cookie(session)).andExpect(status().isTooManyRequests());

        // once confirmed there is nothing more to send
        String other = newEmail();
        Cookie otherSession = sessionOf(register(other));
        mvc.perform(post("/api/auth/verify-email", newIp(), Map.of("token", mail.lastToken(other, "verify")))).andExpect(status().isOk());
        int before = mail.sentTo(other).size();
        mvc.perform(post("/api/auth/resend-verification", newIp(), Map.of()).cookie(otherSession)).andExpect(status().isOk());
        assertThat(mail.sentTo(other)).hasSize(before);
    }
}
