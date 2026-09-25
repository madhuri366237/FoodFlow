package com.foodflow.dto.admin;

import jakarta.validation.constraints.NotNull;

/** PATCH /api/admin/users/{id}/status body: {"enabled": false}. */
public record UserStatusRequest(@NotNull(message = "enabled is required") Boolean enabled) {
}
