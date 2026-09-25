package com.foodflow.service.impl;

import com.foodflow.dto.auth.AccountType;
import com.foodflow.dto.auth.AuthResponse;
import com.foodflow.dto.auth.LoginRequest;
import com.foodflow.dto.auth.RegistrationRequest;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.exception.BadRequestException;
import com.foodflow.exception.ConflictException;
import com.foodflow.repository.UserRepository;
import com.foodflow.security.JwtUtil;
import com.foodflow.security.LoginAttemptLimiter;
import com.foodflow.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test of the business rules only. Mockito replaces the repository, encoder,
 * AuthenticationManager and JwtUtil, so this runs in milliseconds without Spring or a database.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private LoginAttemptLimiter loginAttemptLimiter;

    @InjectMocks
    private AuthServiceImpl authService;

    private static RegistrationRequest registration(String email, String password, AccountType type) {
        return new RegistrationRequest("Asha Rao", email, password, "9876543210", type);
    }

    /** Makes save() behave like the database: return the same user with an id assigned. */
    private void saveAssignsId() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
    }

    @Test
    void registerHashesPasswordNormalisesEmailAndDefaultsToCustomer() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("$2a$10$hashed");
        when(jwtUtil.generateToken(any())).thenReturn("jwt-token");
        when(jwtUtil.getExpirationSeconds()).thenReturn(3600L);
        saveAssignsId();

        AuthResponse response = authService.register(registration("  Asha@Example.com ", "Secret123", null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$hashed").isNotEqualTo("Secret123");
        assertThat(saved.getValue().getEmail()).isEqualTo("asha@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo("asha@example.com");
    }

    @Test
    void registerAsRestaurantOwnerGetsOwnerRole() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        saveAssignsId();

        AuthResponse response = authService.register(
                registration("owner@example.com", "Secret123", AccountType.RESTAURANT_OWNER));

        assertThat(response.user().role()).isEqualTo(Role.RESTAURANT_OWNER);
    }

    @Test
    void registerWithDuplicateEmailIsRejectedAndNothingIsSaved() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registration("TAKEN@example.com", "Secret123", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An account with this email already exists");
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void passwordLongerThan72BytesIsRejected() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        // 30 characters but 88 bytes in UTF-8 (each '密' is 3 bytes): passes @Size(max = 72), fails the byte check.
        String multiByte = "密".repeat(29) + "1";

        assertThatThrownBy(() -> authService.register(registration("a@example.com", multiByte, null)))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithValidCredentialsReturnsToken() {
        User user = new User("Asha", "asha@example.com", "hash", null, Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 7L);
        UserPrincipal principal = UserPrincipal.from(user);
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(principal)).thenReturn("jwt-token");

        AuthResponse response = authService.login(new LoginRequest("Asha@Example.com", "Secret123"), "10.0.0.1");

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.user().id()).isEqualTo(7L);
    }

    @Test
    void loginWithWrongPasswordPropagatesBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("asha@example.com", "wrong"), "10.0.0.1"))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtUtil, never()).generateToken(any());
    }
}
