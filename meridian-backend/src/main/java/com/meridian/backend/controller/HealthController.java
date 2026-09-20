package com.meridian.backend.controller;

import com.meridian.backend.service.PriceFeedStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

// Used by the Docker healthcheck and by anything that monitors the site:
// 200 only when the app can actually reach its database, 503 otherwise.
//
// The body also says how the price feed is doing ("priceFeed": ok / stale / empty and the
// age of the newest price; "stale" means prices should be arriving and are not: a stock market that is
// closed is not stale). That is information for a monitor, not a health failure: the
// site is up and showing its last known prices, and restarting it would not fix the feed.
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbc;
    private final PriceFeedStatus priceFeedStatus;

    public HealthController(JdbcTemplate jdbc, PriceFeedStatus priceFeedStatus) {
        this.jdbc = jdbc;
        this.priceFeedStatus = priceFeedStatus;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            jdbc.queryForObject("select 1", Integer.class);
        } catch (Exception e) {
            log.warn("Health check: the database is not reachable", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "degraded", "service", "meridian-backend", "database", "down"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "meridian-backend");
        body.put("database", "up");
        try {
            PriceFeedStatus.Status feed = priceFeedStatus.check();
            body.put("priceFeed", feed.feed());
            if (feed.newestPriceAgeSeconds() != null) {
                body.put("newestPriceAgeSeconds", feed.newestPriceAgeSeconds());
            }
        } catch (Exception e) {
            body.put("priceFeed", "unknown");
        }
        return ResponseEntity.ok(body);
    }
}
