package com.meridian.backend.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

// Browsers can't send custom headers (like Authorization: Bearer ...) when
// opening a WebSocket connection, but they do send cookies — so the session
// cookie authenticates the handshake. (The token used to travel in the URL as
// ?token=..., which ends up in server logs and browser history; that is no
// longer accepted.) This interceptor runs once, at connection time, validates
// the token, and stores which user owns this connection — so later,
// PriceWebSocketHandler can send some messages (like personal alerts) to only
// that one user's session.
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final SessionAuthenticator sessions;
    private final AuthCookies authCookies;

    public WebSocketAuthInterceptor(SessionAuthenticator sessions, AuthCookies authCookies) {
        this.sessions = sessions;
        this.authCookies = authCookies;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return true; // shouldn't happen in practice, but don't block if it does
        }

        String token = authCookies.extractToken(servletRequest.getServletRequest());
        if (token == null) {
            return true; // allow anonymous connections too — they just won't receive personal alerts
        }

        // An invalid/expired/superseded token on a handshake is treated as
        // anonymous rather than rejecting the connection outright.
        sessions.authenticate(token).ifPresent(user -> attributes.put("userId", user.getId()));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // Nothing needed here
    }
}
