package com.foodflow.dto.user;

import com.foodflow.entity.Role;
import com.foodflow.entity.User;

import java.time.Instant;

/**
 * Public view of a user. passwordHash and other internal fields are simply not
 * part of this type, so they cannot leak into any JSON response.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        String phone,
        Role role,
        boolean enabled,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(),
                user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}
