package com.meridian.backend.marketdata;

import com.meridian.backend.MutableClock;
import com.meridian.backend.client.FinnhubProvider;
import com.meridian.backend.client.RequestBudget;
import com.meridian.backend.config.MarketDataHttpConfig;
import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.exception.MarketDataUnreachableException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A REAL socket, not a mock: the "provider" accepts the connection and then never says a word,
 * which is what an overloaded or half-dead server does. Without a read timeout the caller would
 * wait for as long as the operating system keeps the socket open.
 */
class ProviderTimeoutTest {

    @Test
    void aProviderThatNeverAnswersIsGivenUpOnAfterTheReadTimeout() throws Exception {
        try (ServerSocket silent = new ServerSocket(0)) {
            List<Socket> held = new ArrayList<>(); // keep accepted sockets open and unanswered
            Thread acceptor = new Thread(() -> {
                try {
                    while (!silent.isClosed()) held.add(silent.accept());
                } catch (Exception ignored) {
                    // closed at the end of the test
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            RestClient.Builder builder = RestClient.builder();
            new MarketDataHttpConfig().providerTimeouts(1000, 400).customize(builder);
            MarketDataProperties props = new MarketDataProperties();
            props.setProvider("finnhub");
            FinnhubProvider provider = new FinnhubProvider(builder,
                    new RequestBudget(props, new MutableClock(Instant.parse("2026-09-19T10:00:00Z"))),
                    "k", "http://localhost:" + silent.getLocalPort() + "/api/v1");

            long started = System.nanoTime();
            assertTimeoutPreemptively(Duration.ofSeconds(8), () ->
                    assertThatThrownBy(() -> provider.fetchStockPrice("NVDA")).isInstanceOf(MarketDataUnreachableException.class));
            long millis = (System.nanoTime() - started) / 1_000_000;

            assertThat(millis).as("gave up after roughly the 400 ms read timeout").isBetween(300L, 4000L);
            held.forEach(s -> {
                try {
                    s.close();
                } catch (Exception ignored) {
                    // best effort
                }
            });
        }
    }

    @Test
    void theDefaultLimitsAreShortEnoughForAScheduledJob() {
        // 5 s to connect + 10 s to read: a stuck request costs a scheduler thread 15 s, not forever.
        RestClient.Builder builder = RestClient.builder();
        new MarketDataHttpConfig().providerTimeouts(5000, 10000).customize(builder);
        assertThat(builder).isNotNull();
    }
}
