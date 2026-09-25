package com.foodflow.dto.restaurant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Optional query-string filters for GET /api/restaurants, e.g.
 * {@code ?keyword=biryani&minRating=4&open=true&categoryId=1&minPrice=100&maxPrice=300}.
 * Every field may be absent; absent filters are simply not applied.
 */
public record RestaurantSearchCriteria(

        @Size(max = 100, message = "Keyword must be at most 100 characters")
        String keyword,

        @Positive(message = "categoryId must be positive")
        Long categoryId,

        @DecimalMin(value = "0.0", message = "minRating must be between 0 and 5")
        @DecimalMax(value = "5.0", message = "minRating must be between 0 and 5")
        BigDecimal minRating,

        Boolean open,

        @PositiveOrZero(message = "minPrice must not be negative")
        BigDecimal minPrice,

        @PositiveOrZero(message = "maxPrice must not be negative")
        BigDecimal maxPrice) {

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }

    public boolean hasDishFilter() {
        return categoryId != null || minPrice != null || maxPrice != null;
    }
}
