package com.foodflow.dto.restaurant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Create/update body for a restaurant.
 *
 * <p>Mass-assignment protection: the DTO contains only what an owner may set. owner, rating,
 * ratingCount and active are not fields here, so a request cannot change them, even by
 * adding extra JSON properties (those are ignored).
 */
public record RestaurantRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address must be at most 255 characters")
        String address,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone must be 10 to 15 digits, optionally starting with +")
        String phone,

        // Only http(s): a "javascript:..." URL rendered as an <img src> or link would be an XSS vector.
        @URL(regexp = "^https?://.*", message = "Image URL must be a valid http(s) URL")
        @Size(max = 500, message = "Image URL must be at most 500 characters")
        String imageUrl) {
}
