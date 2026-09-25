package com.foodflow.dto.order;

import jakarta.validation.constraints.Size;

/** POST /api/orders/{id}/cancel body (optional): {"reason":"Ordered by mistake"}. */
public record CancelOrderRequest(
        @Size(max = 255, message = "reason must be at most 255 characters")
        String reason) {
}
