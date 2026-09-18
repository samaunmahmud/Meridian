package com.meridian.backend.security;

import com.meridian.backend.model.User;
import com.meridian.backend.repository.UserRepository;
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

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // strip the "Bearer " prefix

            try {
                String email = jwtUtil.extractEmail(token);
                User user = userRepository.findByEmail(email).orElse(null);

                if (user != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Principal = the actual User entity, so controllers can access
                    // it directly via @AuthenticationPrincipal User user.
                    var authToken = new UsernamePasswordAuthenticationToken(user, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception e) {
                // Invalid/expired token — just leave the request unauthenticated.
                // Spring Security will reject it later if the endpoint requires auth.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
