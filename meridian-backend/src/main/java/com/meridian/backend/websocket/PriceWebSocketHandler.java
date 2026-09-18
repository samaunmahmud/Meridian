package com.meridian.backend.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class PriceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PriceWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket disconnected: {}", session.getId());
    }

    // Sends to EVERY connected browser — correct for price updates, since
    // market data is public and the same for everyone.
    public void broadcast(String json) {
        for (WebSocketSession session : sessions) {
            sendSafely(session, json);
        }
    }

    // Sends only to the sessions belonging to one specific user — used for
    // personal notifications like a triggered price alert, so one user's
    // alert never leaks to another user's browser.
    public void broadcastToUser(Long userId, String json) {
        if (userId == null) return;
        for (WebSocketSession session : sessions) {
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
        } catch (IOException e) {
            log.warn("Failed to send to session {}", session.getId(), e);
        }
    }
}
