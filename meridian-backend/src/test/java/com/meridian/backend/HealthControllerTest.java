package com.meridian.backend;

import com.meridian.backend.controller.HealthController;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.service.PriceFreshness;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void isPublicAndReportsOkWhenTheDatabaseAnswers() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.database").value("up"));
    }

    @Test
    void reportsServiceUnavailableWhenTheDatabaseIsDown() {
        JdbcTemplate broken = mock(JdbcTemplate.class);
        when(broken.queryForObject(anyString(), org.mockito.ArgumentMatchers.<Class<Integer>>any()))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        var response = new HealthController(broken, mock(PriceHistoryRepository.class), mock(PriceFreshness.class)).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "degraded").containsEntry("database", "down");
    }

    // The feed status is information for a monitor: the site itself is fine, so it is never a 503.
    private HealthController withNewestPrice(Optional<PriceHistory> newest, boolean stale) {
        PriceHistoryRepository prices = mock(PriceHistoryRepository.class);
        when(prices.findFirstByOrderByRecordedAtDesc()).thenReturn(newest);
        PriceFreshness freshness = mock(PriceFreshness.class);
        when(freshness.isPriceStale(org.mockito.ArgumentMatchers.any())).thenReturn(stale);
        return new HealthController(mock(JdbcTemplate.class), prices, freshness);
    }

    private static PriceHistory priceRecordedAgo(Duration ago) {
        return new PriceHistory(new Ticker("X", "X Corp", "TEST"), BigDecimal.TEN, Instant.now().minus(ago));
    }

    @Test
    void reportsAFreshPriceFeed() {
        var response = withNewestPrice(Optional.of(priceRecordedAgo(Duration.ofSeconds(30))), false).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("priceFeed", "ok");
        assertThat((Long) response.getBody().get("newestPriceAgeSeconds")).isBetween(29L, 40L);
    }

    @Test
    void reportsAStalePriceFeedWithoutFailingTheHealthCheck() {
        var response = withNewestPrice(Optional.of(priceRecordedAgo(Duration.ofHours(6))), true).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "ok").containsEntry("priceFeed", "stale");
        assertThat((Long) response.getBody().get("newestPriceAgeSeconds")).isGreaterThan(5 * 3600L);
    }

    @Test
    void reportsAnEmptyPriceFeed() {
        var response = withNewestPrice(Optional.empty(), false).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("priceFeed", "empty").doesNotContainKey("newestPriceAgeSeconds");
    }
}
