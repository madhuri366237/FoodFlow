package com.foodflow.dto.review;

import com.foodflow.entity.Review;

import java.time.Instant;

/** Public view of a review: the reviewer's name, never their email, phone or order details. */
public record ReviewResponse(
        Long id,
        int rating,
        String comment,
        String customerName,
        Instant createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getRating(), review.getComment(),
                review.getCustomer().getName(), review.getCreatedAt());
    }
}
