package com.meridian.backend.service;

import com.meridian.backend.dto.AuthResponse;
import com.meridian.backend.dto.LoginRequest;
import com.meridian.backend.dto.RegisterRequest;
import com.meridian.backend.exception.EmailAlreadyExistsException;
import com.meridian.backend.exception.InvalidCredentialsException;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.model.Portfolio;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.UserRepository;
import com.meridian.backend.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AuthService {

    private static final BigDecimal STARTING_CASH = new BigDecimal("10000.00");

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PortfolioRepository portfolioRepository,
                        PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse register(RegisterRequest request) {
        if (request.email() == null || !request.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new InvalidRequestException("Please provide a valid email address");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new InvalidRequestException("Password must be at least 8 characters");
        }
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

        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        // passwordEncoder.matches() hashes the SUBMITTED password the same way and
        // compares hashes — the plain password is never stored anywhere to compare against.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail());
    }
}
