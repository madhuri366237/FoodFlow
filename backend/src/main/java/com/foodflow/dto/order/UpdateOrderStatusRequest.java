package com.foodflow.dto.order;

import com.foodflow.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT /api/orders/{id}/status body, e.g. {"status":"CONFIRMED"}. Validated against the state machine. */
public record UpdateOrderStatusRequest(
        @NotNull(message = "status is required")
        OrderStatus status,

        @Size(max = 255, message = "note must be at most 255 characters")
        String note) {
}
