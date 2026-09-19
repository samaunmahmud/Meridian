package com.meridian.backend.security;

import com.meridian.backend.model.User;
import com.meridian.backend.repository.UserRepository;
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

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final AuthCookies authCookies;

    public WebSocketAuthInterceptor(JwtUtil jwtUtil, UserRepository userRepository, AuthCookies authCookies) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
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

        try {
            String email = jwtUtil.extractEmail(token);
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                attributes.put("userId", user.getId());
            }
        } catch (Exception e) {
            // Invalid/expired token on a WebSocket handshake — just treat as anonymous
            // rather than rejecting the connection outright.
        }

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // Nothing needed here
    }
}
