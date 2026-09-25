package com.foodflow.dto.admin;

import com.foodflow.entity.Restaurant;

import java.math.BigDecimal;
import java.time.Instant;

/** Admin view of a restaurant: unlike the public view, includes who owns it. */
public record AdminRestaurantResponse(
        Long id,
        String name,
        String address,
        Long ownerId,
        String ownerName,
        String ownerEmail,
        BigDecimal rating,
        int ratingCount,
        boolean open,
        boolean active,
        Instant createdAt) {

    /** The owners of a whole page are loaded in one batched query (default_batch_fetch_size). */
    public static AdminRestaurantResponse from(Restaurant restaurant) {
        return new AdminRestaurantResponse(restaurant.getId(), restaurant.getName(), restaurant.getAddress(),
                restaurant.getOwner().getId(), restaurant.getOwner().getName(), restaurant.getOwner().getEmail(),
                restaurant.getRating(), restaurant.getRatingCount(), restaurant.isOpen(), restaurant.isActive(),
                restaurant.getCreatedAt());
    }
}
