package com.meridian.backend.controller;

import com.meridian.backend.dto.AuthResponse;
import com.meridian.backend.dto.ForgotPasswordRequest;
import com.meridian.backend.dto.LoginRequest;
import com.meridian.backend.dto.MessageResponse;
import com.meridian.backend.dto.RegisterRequest;
import com.meridian.backend.dto.ResetPasswordRequest;
import com.meridian.backend.dto.VerifyEmailRequest;
import com.meridian.backend.model.User;
import com.meridian.backend.security.AuthCookies;
import com.meridian.backend.service.AccountService;
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
    private final AccountService accountService;
    private final AuthCookies authCookies;

    public AuthController(AuthService authService, AccountService accountService, AuthCookies authCookies) {
        this.authService = authService;
        this.accountService = accountService;
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
        return new AuthResponse(user.getEmail(), user.isEmailVerified());
    }

    // The answer is the same whether or not the address has an account, so this
    // cannot be used to find out who is registered.
    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        accountService.requestPasswordReset(request.email(), http.getRemoteAddr());
        return new MessageResponse("If an account exists for that email, we've sent a link to reset the password.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@RequestBody ResetPasswordRequest request) {
        accountService.resetPassword(request.token(), request.password());
        return new MessageResponse("Your password has been changed. Please sign in with the new one.");
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@RequestBody VerifyEmailRequest request) {
        accountService.verifyEmail(request.token());
        return new MessageResponse("Your email address is confirmed.");
    }

    // Requires authentication (see SecurityConfig).
    @PostMapping("/resend-verification")
    public MessageResponse resendVerification(@AuthenticationPrincipal User user) {
        accountService.resendVerification(user);
        return new MessageResponse("We've sent a new confirmation link to " + user.getEmail() + ".");
    }

    private ResponseEntity<AuthResponse> signedIn(AuthService.AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.create(result.token()).toString())
                .body(new AuthResponse(result.email(), result.emailVerified()));
    }
}
