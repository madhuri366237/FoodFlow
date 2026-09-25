package com.foodflow.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * POST /api/restaurants/{id}/reviews body: {"orderId": 12, "rating": 5, "comment": "Great biryani"}.
 * The order proves the customer actually ate there.
 */
public record ReviewRequest(

        @NotNull(message = "orderId is required")
        @Positive(message = "orderId must be positive")
        Long orderId,

        @NotNull(message = "rating is required")
        @Min(value = 1, message = "rating must be between 1 and 5")
        @Max(value = 5, message = "rating must be between 1 and 5")
        Integer rating,

        @Size(max = 1000, message = "comment must be at most 1000 characters")
        String comment) {
}
