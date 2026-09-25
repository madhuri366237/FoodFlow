package com.foodflow.dto.restaurant;

import com.foodflow.entity.Restaurant;

import java.math.BigDecimal;
import java.time.Instant;

/** Public view of a restaurant. No owner details: customers don't need them. */
public record RestaurantResponse(
        Long id,
        String name,
        String description,
        String address,
        String phone,
        String imageUrl,
        BigDecimal rating,
        int ratingCount,
        boolean open,
        boolean active,
        Instant createdAt) {

    public static RestaurantResponse from(Restaurant restaurant) {
        return new RestaurantResponse(restaurant.getId(), restaurant.getName(), restaurant.getDescription(),
                restaurant.getAddress(), restaurant.getPhone(), restaurant.getImageUrl(), restaurant.getRating(),
                restaurant.getRatingCount(), restaurant.isOpen(), restaurant.isActive(), restaurant.getCreatedAt());
    }
}
