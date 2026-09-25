package com.foodflow.controller;

import com.foodflow.dto.auth.AuthResponse;
import com.foodflow.dto.auth.LoginRequest;
import com.foodflow.dto.auth.RegistrationRequest;
import com.foodflow.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller: {@code @Valid} rejects bad input (400) before the service runs; the service
 * does the work; exceptions become JSON errors in GlobalExceptionHandler.
 */
@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 201 Created: a new user resource now exists. */
    @Operation(summary = "Register a customer or restaurant owner (returns a JWT)")
    @SecurityRequirements()
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** POST, not GET: credentials must never appear in a URL (URLs end up in logs and browser history). */
    @Operation(summary = "Log in with email and password (returns a JWT)")
    @SecurityRequirements()
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, http.getRemoteAddr()));
    }
}
