package com.foodflow.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The single JSON shape for every error the API returns, e.g.
 * <pre>
 * {"timestamp":"...","status":404,"error":"RESOURCE_NOT_FOUND",
 *  "message":"Restaurant not found","path":"/api/restaurants/10"}
 * </pre>
 * {@code error} is a stable, machine-readable code the frontend can switch on;
 * {@code message} is for humans. {@code fieldErrors} is present only for validation failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }
}
