package com.foodflow.service;

import com.foodflow.dto.user.UserResponse;

public interface UserService {

    /** The profile of the logged-in user. */
    UserResponse getCurrentUser(Long userId);
}
