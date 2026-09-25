package com.foodflow.service.impl;

import com.foodflow.dto.auth.AccountType;
import com.foodflow.dto.auth.AuthResponse;
import com.foodflow.dto.auth.LoginRequest;
import com.foodflow.dto.auth.RegistrationRequest;
import com.foodflow.dto.user.UserResponse;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.exception.BadRequestException;
import com.foodflow.exception.ConflictException;
import com.foodflow.repository.UserRepository;
import com.foodflow.security.JwtUtil;
import com.foodflow.security.LoginAttemptLimiter;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/**
 * Dependencies arrive through the constructor (Lombok generates it for the final fields).
 * Constructor injection makes them explicit and immutable, and lets unit tests pass mocks
 * with a plain {@code new AuthServiceImpl(...)}; no Spring container is needed.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final LoginAttemptLimiter loginAttemptLimiter;

    /*
     * @Transactional: the duplicate check and the INSERT run in one transaction. If anything
     * after the INSERT throws, the new user row is rolled back instead of half-created.
     */
    @Override
    @Transactional
    public AuthResponse register(RegistrationRequest request) {
        String email = User.normalizeEmail(request.email());

        // Fast, friendly check. Two simultaneous registrations can both pass it; the unique
        // constraint uk_users_email then rejects the second (-> 409 in GlobalExceptionHandler).
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BadRequestException("Password is too long");
        }

        User user = new User(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()), // only the BCrypt hash is stored
                request.phone(),
                resolveRole(request.accountType()));
        User saved = userRepository.save(user);

        UserPrincipal principal = UserPrincipal.from(saved);
        return AuthResponse.bearer(jwtUtil.generateToken(principal), jwtUtil.getExpirationSeconds(),
                UserResponse.from(saved));
    }

    /*
     * Role escalation guard. The role is derived on the server from a closed set of
     * self-service account types. ADMIN is not reachable from this method at all; admins are
     * created only by seed data or by another admin.
     */
    private static Role resolveRole(AccountType accountType) {
        if (accountType == null) {
            return Role.CUSTOMER;
        }
        return switch (accountType) {
            case CUSTOMER -> Role.CUSTOMER;
            case RESTAURANT_OWNER -> Role.RESTAURANT_OWNER;
        };
    }

    /*
     * Delegates credential checking to Spring Security instead of comparing passwords by hand.
     * DaoAuthenticationProvider loads the user by email, rejects disabled accounts, and compares
     * with BCrypt in constant time. It also hashes a dummy password when the email doesn't
     * exist, so response timing doesn't reveal which emails are registered.
     * Failures throw BadCredentialsException / DisabledException -> 401 / 403.
     */
    @Override
    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = User.normalizeEmail(request.email());
        // Checked BEFORE verifying the password: once locked, even the right password gets 429,
        // so an attacker can't tell whether a guess was correct.
        loginAttemptLimiter.checkAllowed(email, clientIp);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (BadCredentialsException e) {
            loginAttemptLimiter.recordFailure(email, clientIp);
            throw e;
        }
        loginAttemptLimiter.recordSuccess(email, clientIp);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists"));

        return AuthResponse.bearer(jwtUtil.generateToken(principal), jwtUtil.getExpirationSeconds(),
                UserResponse.from(user));
    }
}
