package com.meridian.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.backend.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    // Origins allowed to call the API from a browser (comma-separated). The
    // session cookie is only sent to these origins, so it must be a specific
    // list — never "*". Set CORS_ALLOWED_ORIGINS for your real domain.
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper,
                          @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:5174}") List<String> allowedOrigins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
        this.allowedOrigins = allowedOrigins;
    }

    // BCrypt is a one-way hashing algorithm designed specifically for
    // passwords — slow on purpose, which makes brute-forcing stolen
    // hashes impractical. Never write your own password hashing.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowCredentials(true); // needed for the session cookie to be sent cross-origin
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // Writes a small JSON error body, matching the shape GlobalExceptionHandler
    // uses everywhere else — so a missing/invalid token and a business-logic
    // error look the same to the frontend, instead of one having a real
    // message and the other returning an empty/blank body.
    private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("message", message)));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tickers/**", "/api/prices/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied"))
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
