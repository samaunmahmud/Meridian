package com.meridian.backend.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Found by a load test: broadcasts are triggered from several threads at once (price and FX polls,
 * alerts, order fills), Tomcat's WebSocket session throws IllegalStateException when two threads
 * send to it at the same moment, and the handler only caught IOException, so the exception cut the
 * broadcast short for every client after that one. With 16 broadcasts at once, about half the
 * messages were never delivered.
 */
class PriceWebSocketHandlerTest {

    private final PriceWebSocketHandler handler = new PriceWebSocketHandler();

    private static class FakeClient {
        final WebSocketSession session = mock(WebSocketSession.class);
        final AtomicInteger delivered = new AtomicInteger();

        FakeClient(String id, Long userId) throws Exception {
            when(session.getId()).thenReturn(id);
            when(session.isOpen()).thenReturn(true);
            when(session.getAttributes()).thenReturn(userId == null ? Map.of() : Map.of("userId", userId));
            doAnswer(inv -> {
                delivered.incrementAndGet();
                return null;
            }).when(session).sendMessage(any(TextMessage.class));
        }
    }

    private FakeClient connect(String id, Long userId) throws Exception {
        FakeClient client = new FakeClient(id, userId);
        handler.afterConnectionEstablished(client.session);
        return client;
    }

    @Test
    void sendsToEveryConnectedClient() throws Exception {
        FakeClient a = connect("a", 1L), b = connect("b", 2L);

        handler.broadcast("{\"kind\":\"PRICE_UPDATE\"}");

        assertThat(a.delivered.get()).isEqualTo(1);
        assertThat(b.delivered.get()).isEqualTo(1);
    }

    @Test
    void aMessageForOneUserOnlyReachesThatUsersSessions() throws Exception {
        FakeClient mine = connect("mine", 7L), theirs = connect("theirs", 8L), mineToo = connect("mine-2", 7L);

        handler.broadcastToUser(7L, "{\"kind\":\"ORDER_FILLED\"}");

        assertThat(mine.delivered.get()).isEqualTo(1);
        assertThat(mineToo.delivered.get()).isEqualTo(1);
        assertThat(theirs.delivered.get()).isZero();
    }

    @Test
    void aClientThatIsGoneStopsReceivingAnything() throws Exception {
        FakeClient gone = connect("gone", 1L);
        handler.afterConnectionClosed(gone.session, CloseStatus.NORMAL);

        handler.broadcast("{}");

        assertThat(gone.delivered.get()).isZero();
    }

    @Test
    void oneClientThatFailsDoesNotStopTheMessageReachingTheOthers() throws Exception {
        List<FakeClient> healthy = new ArrayList<>();
        for (int i = 0; i < 20; i++) healthy.add(connect("ok-" + i, (long) i));
        FakeClient broken = connect("broken", 99L);
        doThrow(new IllegalStateException("The remote endpoint was in state [TEXT_FULL_WRITING]"))
                .when(broken.session).sendMessage(any(TextMessage.class));

        handler.broadcast("{}");

        assertThat(healthy).allSatisfy(c -> assertThat(c.delivered.get()).isEqualTo(1));
    }

    @Test
    void broadcastsFromManyThreadsAtOnceNeverOverlapOnOneSessionAndNothingIsLost() throws Exception {
        // A session that, like Tomcat's, cannot be written by two threads at the same time.
        WebSocketSession strict = mock(WebSocketSession.class);
        when(strict.getId()).thenReturn("strict");
        when(strict.isOpen()).thenReturn(true);
        when(strict.getAttributes()).thenReturn(Map.of());
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger delivered = new AtomicInteger();
        AtomicBoolean overlapped = new AtomicBoolean();
        doAnswer(inv -> {
            if (inFlight.incrementAndGet() > 1) overlapped.set(true);
            Thread.sleep(0, 200_000); // a real write takes a moment
            inFlight.decrementAndGet();
            delivered.incrementAndGet();
            return null;
        }).when(strict).sendMessage(any(TextMessage.class));
        handler.afterConnectionEstablished(strict);

        int threads = 16, perThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> done = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            done.add(pool.submit(() -> {
                for (int i = 0; i < perThread; i++) handler.broadcast("{\"kind\":\"PRICE_UPDATE\"}");
            }));
        }
        for (Future<?> f : done) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(overlapped).as("two threads wrote to the same session at once").isFalse();
        assertThat(delivered.get()).as("messages delivered").isEqualTo(threads * perThread);
    }
}
