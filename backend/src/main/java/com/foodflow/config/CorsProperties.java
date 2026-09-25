package com.foodflow.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** app.cors.allowed-origins, e.g. "http://localhost:5173,https://foodflow.example.com". */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
