package com.foodflow.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/auth/register body.
 *
 * <p>There is no "role" field. Spring Boot's Jackson setup ignores unknown JSON properties,
 * so a client sending "role":"ADMIN" has that value silently dropped. The role is always
 * decided by the server from {@link AccountType}.
 */
public record RegistrationRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        @Schema(example = "Asha Rao")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        @Schema(example = "asha@example.com")
        String email,

        /*
         * BCrypt only uses the first 72 BYTES of a password; anything after that is ignored.
         * Capping the length at 72 means two different long passwords can never hash the same.
         * The service also checks the byte length, because non-ASCII characters take several bytes.
         */
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one digit")
        @Schema(example = "Secret123")
        String password,

        // Optional. 10 to 15 digits with an optional leading '+', e.g. +919876543210.
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone must be 10 to 15 digits, optionally starting with +")
        @Schema(example = "9876543210")
        String phone,

        // Optional; defaults to CUSTOMER.
        @Schema(example = "CUSTOMER", description = "CUSTOMER (default) or RESTAURANT_OWNER; ADMIN is not accepted")
        AccountType accountType) {
}
