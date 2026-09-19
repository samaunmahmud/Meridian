package com.meridian.backend.controller;

import com.meridian.backend.dto.AuthResponse;
import com.meridian.backend.dto.LoginRequest;
import com.meridian.backend.dto.RegisterRequest;
import com.meridian.backend.model.User;
import com.meridian.backend.security.AuthCookies;
import com.meridian.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookies authCookies;

    public AuthController(AuthService authService, AuthCookies authCookies) {
        this.authService = authService;
        this.authCookies = authCookies;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request, HttpServletRequest http) {
        return signedIn(authService.register(request, http.getRemoteAddr()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletRequest http) {
        return signedIn(authService.login(request, http.getRemoteAddr()));
    }

    // Deletes the session cookie. (The browser cannot do this itself: the
    // cookie is HttpOnly, so only the server can clear it.)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, authCookies.clear().toString()).build();
    }

    // Lets the frontend ask "am I signed in?" on page load without ever
    // seeing the token. Requires authentication (see SecurityConfig).
    @GetMapping("/me")
    public AuthResponse me(@AuthenticationPrincipal User user) {
        return new AuthResponse(user.getEmail());
    }

    private ResponseEntity<AuthResponse> signedIn(AuthService.AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.create(result.token()).toString())
                .body(new AuthResponse(result.email()));
    }
}
