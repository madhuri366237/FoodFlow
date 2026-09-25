package com.foodflow.dto.admin;

import jakarta.validation.constraints.NotNull;

/** PATCH /api/admin/restaurants/{id}/status body: {"active": false}. */
public record RestaurantStatusRequest(@NotNull(message = "active is required") Boolean active) {
}
