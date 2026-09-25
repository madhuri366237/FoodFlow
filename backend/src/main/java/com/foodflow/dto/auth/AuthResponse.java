package com.foodflow.dto.auth;

import com.foodflow.dto.user.UserResponse;

/**
 * Returned by both register and login, so a newly registered user is logged in immediately.
 *
 * @param accessToken      the JWT; send it back as "Authorization: Bearer &lt;token&gt;"
 * @param tokenType        always "Bearer"
 * @param expiresInSeconds token lifetime, so the client knows when to log in again
 * @param user             the account, without any password data
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserResponse user) {

    public static AuthResponse bearer(String token, long expiresInSeconds, UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresInSeconds, user);
    }
}
