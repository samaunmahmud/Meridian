package com.meridian.backend.service;

import com.meridian.backend.exception.EmailAlreadyExistsException;
import com.meridian.backend.exception.InvalidCredentialsException;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.dto.LoginRequest;
import com.meridian.backend.dto.RegisterRequest;
import com.meridian.backend.model.Portfolio;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.UserRepository;
import com.meridian.backend.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AuthService {

    private static final BigDecimal STARTING_CASH = new BigDecimal("10000.00");

    /** A signed-in session: the token goes into an HttpOnly cookie, the email into the response body. */
    public record AuthResult(String token, String email, boolean emailVerified) {
    }

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttempts;
    private final AccountService accountService;

    // Compared against when the email doesn't exist, so "no such account" takes
    // as long as "wrong password" — otherwise response time would reveal which
    // emails are registered.
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PortfolioRepository portfolioRepository,
                        PasswordEncoder passwordEncoder, JwtUtil jwtUtil, LoginAttemptService loginAttempts,
                        AccountService accountService) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.loginAttempts = loginAttempts;
        this.accountService = accountService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // One transaction: a user is never left without their portfolio.
    @Transactional
    public AuthResult register(RegisterRequest request, String clientIp) {
        loginAttempts.checkAndRecordRegistration(clientIp);

        if (request.email() == null || !request.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new InvalidRequestException("Please provide a valid email address");
        }
        PasswordPolicy.validate(request.password());
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        // Hash the password before it ever touches the database — passwordEncoder.encode()
        // runs BCrypt, turning "mypassword123" into something like
        // "$2a$10$N9qo8uLOickgx2ZMRZoMy..." which cannot be reversed back to the original.
        String hash = passwordEncoder.encode(request.password());
        User user = userRepository.save(new User(request.email(), hash));

        // Every new account gets its own portfolio immediately, seeded with
        // virtual starting cash, so there's nothing left in a half-set-up state.
        portfolioRepository.save(new Portfolio(user, STARTING_CASH));
        accountService.sendVerification(user); // emailed once this transaction commits

        return new AuthResult(jwtUtil.generateToken(user.getEmail()), user.getEmail(), user.isEmailVerified());
    }

    public AuthResult login(LoginRequest request, String clientIp) {
        // Refuse before doing any password work if this email or IP has failed too often.
        loginAttempts.checkLoginAllowed(request.email(), clientIp);

        User user = request.email() == null ? null : userRepository.findByEmail(request.email()).orElse(null);
        String password = request.password() == null ? "" : request.password();

        // passwordEncoder.matches() hashes the SUBMITTED password the same way and
        // compares hashes — the plain password is never stored anywhere to compare against.
        boolean valid = passwordEncoder.matches(password, user != null ? user.getPasswordHash() : dummyHash) && user != null;
        if (!valid) {
            loginAttempts.recordFailure(request.email(), clientIp);
            throw new InvalidCredentialsException();
        }

        loginAttempts.recordSuccess(request.email());
        return new AuthResult(jwtUtil.generateToken(user.getEmail()), user.getEmail(), user.isEmailVerified());
    }
}
