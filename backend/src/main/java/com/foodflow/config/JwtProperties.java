package com.foodflow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe binding of the app.jwt.* properties.
 * {@code @Validated} makes startup fail immediately with a clear message if a value is
 * missing or invalid, instead of failing later on the first login.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Positive long expirationMs,
        @NotBlank String issuer) {
}
