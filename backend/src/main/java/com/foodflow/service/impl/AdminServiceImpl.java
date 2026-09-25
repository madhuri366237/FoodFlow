package com.foodflow.service.impl;

import com.foodflow.dto.admin.AdminRestaurantResponse;
import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.user.UserResponse;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.exception.BadRequestException;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.RestaurantRepository;
import com.foodflow.repository.RestaurantSpecifications;
import com.foodflow.repository.UserRepository;
import com.foodflow.repository.UserSpecifications;
import com.foodflow.service.AdminService;
import com.foodflow.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Admin moderation. Nothing is ever hard-deleted: users are DISABLED and restaurants
 * DEACTIVATED, so order history, payments and reviews stay intact.
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final Set<String> USER_SORTS = Set.of("name", "email", "createdAt", "role");
    private static final Set<String> RESTAURANT_SORTS = Set.of("name", "rating", "createdAt");

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(Role role, Boolean enabled, String keyword, Pageable pageable) {
        List<Specification<User>> filters = new ArrayList<>();
        if (role != null) filters.add(UserSpecifications.roleIs(role));
        if (enabled != null) filters.add(UserSpecifications.enabledIs(enabled));
        if (keyword != null && !keyword.isBlank()) filters.add(UserSpecifications.matches(keyword));
        return PageResponse.from(userRepository
                .findAll(Specification.allOf(filters), PageableUtils.sanitize(pageable, USER_SORTS))
                .map(UserResponse::from));
    }

    /*
     * Takes effect immediately: JwtFilter reloads the user on every request (Phase 3), so a
     * disabled user's existing token stops working on their very next call. No token blacklist needed.
     */
    @Override
    @Transactional
    public UserResponse setUserEnabled(Long adminId, Long userId, boolean enabled) {
        if (adminId.equals(userId) && !enabled) {
            throw new BadRequestException("You cannot disable your own account");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        user.setEnabled(enabled);
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminRestaurantResponse> getRestaurants(Boolean active, String keyword, Pageable pageable) {
        List<Specification<Restaurant>> filters = new ArrayList<>();
        if (active != null) filters.add(RestaurantSpecifications.activeIs(active));
        if (keyword != null && !keyword.isBlank()) filters.add(RestaurantSpecifications.matchesKeyword(keyword));
        return PageResponse.from(restaurantRepository
                .findAll(Specification.allOf(filters), PageableUtils.sanitize(pageable, RESTAURANT_SORTS))
                .map(AdminRestaurantResponse::from));
    }

    /*
     * A deactivated restaurant disappears from search and details, and can't receive new
     * orders (cart and checkout both check "active"). Its existing orders and history remain.
     */
    @Override
    @Transactional
    public AdminRestaurantResponse setRestaurantActive(Long restaurantId, boolean active) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> ResourceNotFoundException.of("Restaurant", restaurantId));
        restaurant.setActive(active);
        return AdminRestaurantResponse.from(restaurantRepository.saveAndFlush(restaurant));
    }
}
