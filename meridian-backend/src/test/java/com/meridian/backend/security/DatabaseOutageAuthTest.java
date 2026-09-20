package com.meridian.backend.security;

import com.meridian.backend.model.User;
import com.meridian.backend.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Found by stopping the database during a load test: GET /api/portfolio answered 401, because a
 * database failure while checking the session was treated as "not a valid session". The browser
 * reads a 401 as "you have been signed out", so a short database outage looked like everyone's
 * session ending. It must be a 503, which the browser shows as an error without signing anyone out.
 */
class DatabaseOutageAuthTest {

    private final JwtUtil jwt = mock(JwtUtil.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SessionAuthenticator sessions = new SessionAuthenticator(jwt, users);
    private final AuthCookies cookies = mock(AuthCookies.class);

    private io.jsonwebtoken.Claims claimsFor(String email) {
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn(email);
        return claims;
    }

    @Test
    void anInvalidTokenIsStillJustNotASession() {
        when(jwt.parse("garbage")).thenThrow(new JwtException("bad signature"));

        assertThat(sessions.authenticate("garbage")).isEmpty();
    }

    @Test
    void anUnknownUserIsStillJustNotASession() {
        io.jsonwebtoken.Claims claims = claimsFor("nobody@example.com");
        when(jwt.parse("t")).thenReturn(claims);
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThat(sessions.authenticate("t")).isEmpty();
    }

    @Test
    void aDatabaseFailureIsNotReportedAsAnInvalidSession() {
        io.jsonwebtoken.Claims claims = claimsFor("me@example.com");
        when(jwt.parse("t")).thenReturn(claims);
        when(users.findByEmail(anyString())).thenThrow(new DataAccessResourceFailureException("connection refused"));

        assertThatThrownBy(() -> sessions.authenticate("t")).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void theFilterAnswers503WhenItCannotReachTheDatabaseAndDoesNotContinue() throws Exception {
        SessionAuthenticator failing = mock(SessionAuthenticator.class);
        when(failing.authenticate(anyString())).thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager"));
        when(cookies.extractToken(any())).thenReturn("a-token");
        JwtAuthFilter filter = new JwtAuthFilter(failing, cookies);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/portfolio");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("temporarily unavailable");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void anExpiredSessionIsStillAnUnauthenticatedRequestNotA503() throws Exception {
        SessionAuthenticator expired = mock(SessionAuthenticator.class);
        when(expired.authenticate(anyString())).thenReturn(Optional.<User>empty());
        when(cookies.extractToken(any())).thenReturn("old-token");
        JwtAuthFilter filter = new JwtAuthFilter(expired, cookies);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/portfolio"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200); // the filter itself does not reject; Spring Security answers 401 later
        verify(chain).doFilter(any(), any());
    }
}
