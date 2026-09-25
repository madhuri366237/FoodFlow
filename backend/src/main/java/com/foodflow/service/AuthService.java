package com.foodflow.service;

import com.foodflow.dto.auth.AuthResponse;
import com.foodflow.dto.auth.LoginRequest;
import com.foodflow.dto.auth.RegistrationRequest;

/**
 * Account registration and login.
 *
 * <p>Controllers depend on this interface, not on AuthServiceImpl. Tests can mock it,
 * and the implementation can change without touching callers (dependency inversion).
 */
public interface AuthService {

    AuthResponse register(RegistrationRequest request);

    /** clientIp is used for brute-force protection (see LoginAttemptLimiter). */
    AuthResponse login(LoginRequest request, String clientIp);
}
