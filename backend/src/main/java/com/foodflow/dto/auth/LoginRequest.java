package com.foodflow.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/auth/login body. Password rules are not re-checked here; only its presence. */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Schema(example = "demo@foodflow.dev")
        String email,

        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password must be at most 72 characters")
        @Schema(example = "Secret123")
        String password) {
}
