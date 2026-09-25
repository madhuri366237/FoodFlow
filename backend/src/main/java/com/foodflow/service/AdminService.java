package com.foodflow.service;

import com.foodflow.dto.admin.AdminRestaurantResponse;
import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.user.UserResponse;
import com.foodflow.entity.Role;
import org.springframework.data.domain.Pageable;

public interface AdminService {

    PageResponse<UserResponse> getUsers(Role role, Boolean enabled, String keyword, Pageable pageable);

    UserResponse setUserEnabled(Long adminId, Long userId, boolean enabled);

    PageResponse<AdminRestaurantResponse> getRestaurants(Boolean active, String keyword, Pageable pageable);

    AdminRestaurantResponse setRestaurantActive(Long restaurantId, boolean active);
}
