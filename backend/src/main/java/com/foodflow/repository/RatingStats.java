package com.foodflow.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * SUM and COUNT of a restaurant's ratings, straight from the database.
 * Both are exact integers; the division happens once, here, in BigDecimal.
 */
public record RatingStats(long sum, long count) {

    /** Average rounded half-up to one decimal (the NUMERIC(2,1) column), 0.0 with no reviews. */
    public BigDecimal average() {
        if (count == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }
}
