package com.foodflow.dto.menu;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

/**
 * Create/update body for a dish. The restaurant comes from the URL on create
 * (POST /api/restaurants/{id}/menu-items) and can never be changed afterwards,
 * so it is not a field here.
 */
public record MenuItemRequest(

        @NotNull(message = "categoryId is required")
        @Positive(message = "categoryId must be positive")
        Long categoryId,

        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        // Mirrors the NUMERIC(10,2) column and its CHECK (price > 0), so a bad price
        // is a clear 400 here instead of a database error later.
        @NotNull(message = "Price is required")
        @Positive(message = "Price must be greater than 0")
        @DecimalMax(value = "100000.00", message = "Price must be at most 100000")
        @Digits(integer = 6, fraction = 2, message = "Price must have at most 2 decimal places")
        BigDecimal price,

        @URL(regexp = "^https?://.*", message = "Image URL must be a valid http(s) URL")
        @Size(max = 500, message = "Image URL must be at most 500 characters")
        String imageUrl,

        // Optional on create (defaults to true).
        Boolean available) {
}
