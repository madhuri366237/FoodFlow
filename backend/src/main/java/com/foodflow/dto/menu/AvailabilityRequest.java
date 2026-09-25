package com.foodflow.dto.menu;

import jakarta.validation.constraints.NotNull;

/** PATCH /api/menu-items/{id}/availability: mark a dish sold out or back in stock. */
public record AvailabilityRequest(@NotNull(message = "available is required") Boolean available) {
}
