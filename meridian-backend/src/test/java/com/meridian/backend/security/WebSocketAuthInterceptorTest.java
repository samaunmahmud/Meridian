package com.meridian.backend.security;

import com.meridian.backend.model.User;
import com.meridian.backend.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketAuthInterceptorTest {

    private final JwtUtil jwt = new JwtUtil("test-secret-test-secret-test-secret-1234567890");
    private final UserRepository users = mock(UserRepository.class);
    private final WebSocketAuthInterceptor interceptor =
            new WebSocketAuthInterceptor(new SessionAuthenticator(jwt, users), new AuthCookies(jwt, false, "Lax"));

    private Map<String, Object> handshake(MockHttpServletRequest request) {
        Map<String, Object> attributes = new HashMap<>();
        boolean allowed = interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);
        assertThat(allowed).isTrue(); // anonymous price-only connections are still allowed
        return attributes;
    }

    private User knownUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(users.findByEmail("a@b.io")).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void theSessionCookieIdentifiesTheConnectionsOwner() {
        knownUser();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookies.NAME, jwt.generateToken("a@b.io")));

        assertThat(handshake(request)).containsEntry("userId", 42L);
    }

    @Test
    void aSessionIssuedBeforeAPasswordChangeNoLongerIdentifiesTheOwner() {
        User user = knownUser();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookies.NAME, jwt.generateToken("a@b.io")));
        when(user.getPasswordChangedAt()).thenReturn(Instant.now().plusSeconds(5)); // changed after the token was issued

        assertThat(handshake(request)).doesNotContainKey("userId");
    }

    @Test
    void aTokenInTheUrlIsNoLongerAccepted() {
        knownUser();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("token", jwt.generateToken("a@b.io"));

        assertThat(handshake(request)).doesNotContainKey("userId");
    }

    @Test
    void aGarbageCookieJustMeansAnonymous() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookies.NAME, "not-a-jwt"));

        assertThat(handshake(request)).doesNotContainKey("userId");
    }
}
