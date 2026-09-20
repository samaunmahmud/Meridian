package com.meridian.backend;

import com.meridian.backend.controller.HealthController;
import com.meridian.backend.service.PriceFeedStatus;
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

        var response = new HealthController(broken, mock(PriceFeedStatus.class)).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "degraded").containsEntry("database", "down");
    }

    // The feed status is information for a monitor: the site itself is fine, so it is never a 503.
    private HealthController withFeed(PriceFeedStatus.Status status) {
        PriceFeedStatus feed = mock(PriceFeedStatus.class);
        when(feed.check()).thenReturn(status);
        return new HealthController(mock(JdbcTemplate.class), feed);
    }

    @Test
    void reportsAFreshPriceFeed() {
        var response = withFeed(new PriceFeedStatus.Status("ok", 30L)).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("priceFeed", "ok").containsEntry("newestPriceAgeSeconds", 30L);
    }

    @Test
    void reportsAStalePriceFeedWithoutFailingTheHealthCheck() {
        var response = withFeed(new PriceFeedStatus.Status("stale", 21600L)).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "ok").containsEntry("priceFeed", "stale");
    }

    @Test
    void reportsAnEmptyPriceFeed() {
        var response = withFeed(new PriceFeedStatus.Status("empty", null)).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("priceFeed", "empty").doesNotContainKey("newestPriceAgeSeconds");
    }

    @Test
    void aFailureCheckingTheFeedNeverBreaksTheHealthCheck() {
        PriceFeedStatus feed = mock(PriceFeedStatus.class);
        when(feed.check()).thenThrow(new IllegalStateException("boom"));

        var response = new HealthController(mock(JdbcTemplate.class), feed).health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("priceFeed", "unknown");
    }
}
