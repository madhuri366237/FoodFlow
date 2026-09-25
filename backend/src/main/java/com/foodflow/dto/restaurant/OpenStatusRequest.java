package com.foodflow.dto.restaurant;

import jakarta.validation.constraints.NotNull;

/** PATCH /api/restaurants/{id}/open-status: the owner starts or stops accepting orders. */
public record OpenStatusRequest(@NotNull(message = "open is required") Boolean open) {
}
