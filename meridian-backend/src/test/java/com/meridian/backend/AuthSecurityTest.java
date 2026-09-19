package com.meridian.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    /** Every test uses its own client IP so their rate-limit counters never mix. */
    private static String newIp() {
        int n = NEXT_IP.incrementAndGet();
        return "10.20." + (n / 250) + "." + (n % 250 + 1);
    }

    private static String newEmail() {
        return UUID.randomUUID() + "@test.io";
    }

    private MockHttpServletRequestBuilder jsonPost(String url, String ip, Object body, boolean csrfHeader) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .with(r -> { r.setRemoteAddr(ip); return r; });
        return csrfHeader ? request.header("X-Requested-With", "meridian") : request;
    }

    private MvcResult register(String email, String ip) throws Exception {
        return mvc.perform(jsonPost("/api/auth/register", ip, Map.of("email", email, "password", "correct-horse-battery"), true)).andReturn();
    }

    private MvcResult login(String email, String password, String ip) throws Exception {
        return mvc.perform(jsonPost("/api/auth/login", ip, Map.of("email", email, "password", password), true)).andReturn();
    }

    private Cookie sessionCookie(MvcResult result) {
        return MockCookie.parse(result.getResponse().getHeader("Set-Cookie"));
    }

    // ---------------------------------------------------------------- cookie session

    @Test
    void registeringSetsAnHttpOnlyCookieAndNeverPutsTheTokenInTheBody() throws Exception {
        String email = newEmail();

        MvcResult result = mvc.perform(jsonPost("/api/auth/register", newIp(), Map.of("email", email, "password", "correct-horse-battery"), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("meridian_token=").contains("HttpOnly").contains("SameSite=Lax")
                .contains("Path=/").contains("Max-Age=86400").doesNotContain("Secure");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("eyJ"); // no JWT anywhere in the body
    }

    @Test
    void theCookieAuthenticatesRequestsAndMeReportsWhoIsSignedIn() throws Exception {
        String email = newEmail();
        Cookie session = sessionCookie(register(email, newIp()));

        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(MockMvcRequestBuilders.get("/api/portfolio").cookie(session))
                .andExpect(status().isOk());
    }

    @Test
    void withoutACookieProtectedEndpointsAreRefused() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/portfolio")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void aTamperedCookieIsRefused() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/portfolio").cookie(new Cookie("meridian_token", "not.a.token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutExpiresTheCookie() throws Exception {
        Cookie session = sessionCookie(register(newEmail(), newIp()));

        mvc.perform(jsonPost("/api/auth/logout", newIp(), Map.of(), true).cookie(session))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("meridian_token="),
                        org.hamcrest.Matchers.containsString("Max-Age=0"),
                        org.hamcrest.Matchers.containsString("HttpOnly"))));
    }

    // ---------------------------------------------------------------- CSRF

    @Test
    void aCookieAuthenticatedRequestThatChangesDataNeedsTheCustomHeader() throws Exception {
        Cookie session = sessionCookie(register(newEmail(), newIp()));
        Map<String, Object> deposit = Map.of("currency", "USD", "amount", 10);

        // what a forged cross-site request looks like: cookie present, custom header absent
        mvc.perform(jsonPost("/api/wallets/deposit", newIp(), deposit, false).cookie(session))
                .andExpect(status().isForbidden());

        mvc.perform(jsonPost("/api/wallets/deposit", newIp(), deposit, true).cookie(session))
                .andExpect(status().isOk());
    }

    @Test
    void readingDataNeverNeedsTheCustomHeader() throws Exception {
        Cookie session = sessionCookie(register(newEmail(), newIp()));

        mvc.perform(MockMvcRequestBuilders.get("/api/wallets").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void aBearerTokenStillWorksForApiClientsWithoutTheHeader() throws Exception {
        Cookie session = sessionCookie(register(newEmail(), newIp()));

        mvc.perform(jsonPost("/api/wallets/deposit", newIp(), Map.of("currency", "USD", "amount", 5), false)
                        .header("Authorization", "Bearer " + session.getValue()))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- brute force

    @Test
    void fiveWrongPasswordsLockTheAccountEvenAgainstTheRightPassword() throws Exception {
        String email = newEmail();
        register(email, newIp());
        String ip = newIp();

        for (int i = 0; i < 5; i++) {
            mvc.perform(jsonPost("/api/auth/login", ip, Map.of("email", email, "password", "wrong-password-" + i), true))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }

        mvc.perform(jsonPost("/api/auth/login", ip, Map.of("email", email, "password", "correct-horse-battery"), true))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Too many failed login attempts")));
    }

    @Test
    void lockingOneAccountDoesNotLockOthers() throws Exception {
        String victim = newEmail();
        String other = newEmail();
        register(victim, newIp());
        register(other, newIp());
        String ip = newIp();
        for (int i = 0; i < 5; i++) login(victim, "wrong-" + i, ip);

        mvc.perform(jsonPost("/api/auth/login", ip, Map.of("email", other, "password", "correct-horse-battery"), true))
                .andExpect(status().isOk());
    }

    @Test
    void aSuccessfulLoginClearsTheFailureCount() throws Exception {
        String email = newEmail();
        register(email, newIp());
        String ip = newIp();

        for (int i = 0; i < 4; i++) login(email, "wrong-" + i, ip);
        assertThat(login(email, "correct-horse-battery", ip).getResponse().getStatus()).isEqualTo(200);
        for (int i = 0; i < 4; i++) {
            assertThat(login(email, "wrong-again-" + i, ip).getResponse().getStatus()).isEqualTo(401); // not 429
        }
    }

    @Test
    void manyFailuresFromOneIpLockThatIpOut() throws Exception {
        String realUser = newEmail();
        register(realUser, newIp());
        String attackerIp = newIp();

        for (int i = 0; i < 20; i++) {
            login("nobody-" + i + "@test.io", "guess", attackerIp); // 20 different unknown accounts
        }

        mvc.perform(jsonPost("/api/auth/login", attackerIp, Map.of("email", realUser, "password", "correct-horse-battery"), true))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void unknownAccountsAndWrongPasswordsLookIdentical() throws Exception {
        String email = newEmail();
        register(email, newIp());

        String unknown = login(newEmail(), "whatever-password", newIp()).getResponse().getContentAsString();
        String wrongPassword = login(email, "whatever-password", newIp()).getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(wrongPassword);
    }

    @Test
    void signUpsFromOneIpAreLimited() throws Exception {
        String ip = newIp();
        for (int i = 0; i < 10; i++) {
            assertThat(register(newEmail(), ip).getResponse().getStatus()).isEqualTo(200);
        }

        mvc.perform(jsonPost("/api/auth/register", ip, Map.of("email", newEmail(), "password", "correct-horse-battery"), true))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void signUpsThatFailStillCountTowardsTheLimit() throws Exception {
        // register() is transactional and throws on a duplicate email; the
        // attempt must stay counted even though that transaction rolls back.
        String ip = newIp();
        String email = newEmail();
        assertThat(register(email, ip).getResponse().getStatus()).isEqualTo(200);
        for (int i = 0; i < 9; i++) {
            int status = register(email, ip).getResponse().getStatus();
            assertThat(status).isNotIn(200, 429); // rejected as a duplicate, not yet rate limited
        }

        mvc.perform(jsonPost("/api/auth/register", ip, Map.of("email", newEmail(), "password", "correct-horse-battery"), true))
                .andExpect(status().isTooManyRequests());
    }
}
