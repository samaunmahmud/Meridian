package com.meridian.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

/** Changing the password and deleting the account from Settings, over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSettingsTest {

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
    @Autowired UserRepository users;
    @Autowired PortfolioRepository portfolios;
    @Autowired JdbcTemplate jdbc;

    private static String newIp() {
        int n = NEXT_IP.incrementAndGet();
        return "10.40." + (n / 250) + "." + (n % 250 + 1);
    }

    private MockHttpServletRequestBuilder send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        String ip = newIp();
        return request.contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .header("X-Requested-With", "meridian")
                .with(r -> { r.setRemoteAddr(ip); return r; });
    }

    private MvcResult register(String email) throws Exception {
        return mvc.perform(send(MockMvcRequestBuilders.post("/api/auth/register"), Map.of("email", email, "password", PASSWORD))).andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(send(MockMvcRequestBuilders.post("/api/auth/login"), Map.of("email", email, "password", password))).andReturn();
    }

    private MvcResult changePassword(Cookie session, String current, String next) throws Exception {
        return mvc.perform(send(MockMvcRequestBuilders.post("/api/auth/change-password"),
                Map.of("currentPassword", current, "newPassword", next)).cookie(session)).andReturn();
    }

    private MvcResult deleteAccount(Cookie session, String password) throws Exception {
        return mvc.perform(send(MockMvcRequestBuilders.delete("/api/auth/account"),
                Map.of("password", password)).cookie(session)).andReturn();
    }

    private int me(Cookie session) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").cookie(session)).andReturn().getResponse().getStatus();
    }

    private static Cookie sessionOf(MvcResult result) {
        return MockCookie.parse(result.getResponse().getHeader("Set-Cookie"));
    }

    private static String newEmail() {
        return UUID.randomUUID() + "@test.io";
    }

    // ---------------------------------------------------------------- change password

    @Test
    void changingThePasswordKeepsThisSessionAndEndsTheOthers() throws Exception {
        String email = newEmail();
        Cookie here = sessionOf(register(email));
        Cookie elsewhere = sessionOf(login(email, PASSWORD));

        Thread.sleep(1100); // session times are whole seconds: make sure the change lands in a later one
        MvcResult result = changePassword(here, PASSWORD, "brand-new-password-1");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(me(sessionOf(result))).isEqualTo(200); // the new cookie works
        assertThat(me(here)).isEqualTo(401);
        assertThat(me(elsewhere)).isEqualTo(401);
        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(login(email, "brand-new-password-1").getResponse().getStatus()).isEqualTo(200);
        assertThat(mail.lastTo(email).subject()).contains("password was changed");
    }

    @Test
    void aWrongCurrentPasswordIsRefusedAndCountsTowardsTheLockout() throws Exception {
        String email = newEmail();
        Cookie session = sessionOf(register(email));

        MvcResult wrong = changePassword(session, "not-my-password", "brand-new-password-1");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(400);
        assertThat(wrong.getResponse().getContentAsString()).contains("current password is incorrect");
        assertThat(me(session)).isEqualTo(200); // a typo doesn't sign you out

        for (int i = 0; i < 4; i++) {
            changePassword(session, "not-my-password-" + i, "brand-new-password-1");
        }
        assertThat(changePassword(session, PASSWORD, "brand-new-password-1").getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void theNewPasswordMustFollowThePolicyAndDiffer() throws Exception {
        Cookie session = sessionOf(register(newEmail()));
        assertThat(changePassword(session, PASSWORD, "short").getResponse().getStatus()).isEqualTo(400);
        assertThat(changePassword(session, PASSWORD, PASSWORD).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void settingsNeedASession() throws Exception {
        MvcResult change = mvc.perform(send(MockMvcRequestBuilders.post("/api/auth/change-password"),
                Map.of("currentPassword", PASSWORD, "newPassword", "brand-new-password-1"))).andReturn();
        MvcResult delete = mvc.perform(send(MockMvcRequestBuilders.delete("/api/auth/account"),
                Map.of("password", PASSWORD))).andReturn();
        assertThat(change.getResponse().getStatus()).isEqualTo(401);
        assertThat(delete.getResponse().getStatus()).isEqualTo(401);
    }

    // ---------------------------------------------------------------- delete account

    @Test
    void deletingTheAccountRemovesEverythingAndTheEmailCanSignUpAgain() throws Exception {
        String email = newEmail();
        Cookie session = sessionOf(register(email));
        Long userId = users.findByEmail(email).orElseThrow().getId();
        Long portfolioId = portfolios.findByUserId(userId).orElseThrow().getId();

        // Give the account something in most of its tables.
        mvc.perform(send(MockMvcRequestBuilders.post("/api/wallets/deposit"), Map.of("currency", "EUR", "amount", 50))
                .cookie(session)).andReturn();
        mvc.perform(send(MockMvcRequestBuilders.post("/api/watchlist"), Map.of("symbol", "AAPL")).cookie(session)).andReturn();
        String dividendSymbol = "D" + Math.abs(email.hashCode() % 1_000_000);
        jdbc.update("insert into tickers (symbol, name, exchange, asset_type) values (?, 'Dividend payer', 'TEST', 'STOCK')", dividendSymbol);
        Long tickerId = jdbc.queryForObject("select id from tickers where symbol = ?", Long.class, dividendSymbol);
        jdbc.update("insert into dividends (ticker_id, ex_date, pay_date, amount) values (?, current_date, current_date, 0.5)", tickerId);
        Long dividendId = jdbc.queryForObject("select id from dividends where ticker_id = ?", Long.class, tickerId);
        jdbc.update("insert into allocation_targets (portfolio_id, ticker_id, target_percent) values (?, ?, 25)", portfolioId, tickerId);
        jdbc.update("insert into dividend_payments (portfolio_id, dividend_id, shares, amount, paid_at) values (?, ?, 2, 1, current_timestamp)",
                portfolioId, dividendId);

        assertThat(deleteAccount(session, "wrong-password").getResponse().getStatus()).isEqualTo(400);
        assertThat(users.findByEmail(email)).isPresent();

        MvcResult result = deleteAccount(session, PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");

        assertThat(users.findByEmail(email)).isEmpty();
        assertThat(portfolios.findById(portfolioId)).isEmpty();
        for (String table : new String[]{"wallets", "transactions", "orders", "holdings", "dividend_payments", "allocation_targets"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where portfolio_id = ?", Integer.class, portfolioId))
                    .as(table).isZero();
        }
        for (String table : new String[]{"watchlist_items", "alerts", "auth_tokens"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, userId))
                    .as(table).isZero();
        }
        assertThat(me(session)).isEqualTo(401);
        assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(register(email).getResponse().getStatus()).isEqualTo(200);
        assertThat(mail.sentTo(email)).anyMatch(m -> m.subject().contains("account was deleted"));
    }
}
