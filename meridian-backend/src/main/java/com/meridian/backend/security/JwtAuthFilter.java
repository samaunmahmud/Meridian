package com.meridian.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Runs on EVERY incoming request, exactly once (that's what "OncePerRequestFilter"
// guarantees). Its job: look for "Authorization: Bearer <token>", and if a
// valid token is present, tell Spring Security "this request is authenticated,
// and here's the User it belongs to" — so controllers can trust the request.
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    // Cookie-authenticated requests that change data must carry this header.
    // A page on another website can make the browser send our cookie, but it
    // cannot add a custom header without our server's CORS permission — so its
    // forged requests are refused (this is the "custom header" CSRF defence,
    // on top of the cookie's SameSite setting).
    public static final String CSRF_HEADER = "X-Requested-With";

    private final SessionAuthenticator sessions;
    private final AuthCookies authCookies;

    public JwtAuthFilter(SessionAuthenticator sessions, AuthCookies authCookies) {
        this.sessions = sessions;
        this.authCookies = authCookies;
    }

    private static boolean changesData(HttpServletRequest request) {
        String method = request.getMethod();
        return !(method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS") || method.equals("TRACE"));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = null;
        boolean viaCookie = false;

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7); // strip the "Bearer " prefix
        } else {
            token = authCookies.extractToken(request);
            viaCookie = token != null;
        }

        if (viaCookie && changesData(request)) {
            String header = request.getHeader(CSRF_HEADER);
            if (header == null || header.isBlank()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Missing " + CSRF_HEADER + " header\"}");
                return;
            }
        }

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // An invalid, expired or superseded token just leaves the request
            // unauthenticated; Spring Security rejects it later if the endpoint needs auth.
            sessions.authenticate(token).ifPresent(user -> {
                // Principal = the actual User entity, so controllers can access
                // it directly via @AuthenticationPrincipal User user.
                var authToken = new UsernamePasswordAuthenticationToken(user, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authToken);
            });
        }

        filterChain.doFilter(request, response);
    }
}
