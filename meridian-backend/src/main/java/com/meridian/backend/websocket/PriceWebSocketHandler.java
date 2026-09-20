package com.meridian.backend.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PriceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PriceWebSocketHandler.class);

    // A client that takes longer than this to accept a message, or falls this many bytes behind,
    // is disconnected instead of holding up everyone else (the browser reconnects by itself).
    static final int SEND_TIME_LIMIT_MS = 10_000;
    static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    // Keyed by session id. Each value wraps the real session so that sends to it are one at a
    // time: Tomcat's WebSocket session throws IllegalStateException if two threads send to it at
    // once, and broadcasts do come from several threads (price and FX polls, alerts, order fills).
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES));
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WebSocket disconnected: {}", session.getId());
    }

    // Sends to EVERY connected browser — correct for price updates, since
    // market data is public and the same for everyone.
    public void broadcast(String json) {
        for (WebSocketSession session : sessions.values()) {
            sendSafely(session, json);
        }
    }

    // Sends only to the sessions belonging to one specific user — used for
    // personal notifications like a triggered price alert, so one user's
    // alert never leaks to another user's browser.
    public void broadcastToUser(Long userId, String json) {
        if (userId == null) return;
        for (WebSocketSession session : sessions.values()) {
            Object sessionUserId = session.getAttributes().get("userId");
            if (Objects.equals(sessionUserId, userId)) {
                sendSafely(session, json);
            }
        }
    }

    private void sendSafely(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            // Whatever goes wrong with ONE client (closed socket, too slow, over its buffer limit)
            // must never stop the message reaching the others.
            log.warn("Could not send to WebSocket session {}: {}", session.getId(), e.toString());
        }
    }
}
