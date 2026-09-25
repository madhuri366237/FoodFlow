package com.foodflow.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CategoryRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "Name must be at most 50 characters")
        String name,

        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @URL(regexp = "^https?://.*", message = "Image URL must be a valid http(s) URL")
        @Size(max = 500, message = "Image URL must be at most 500 characters")
        String imageUrl) {
}
