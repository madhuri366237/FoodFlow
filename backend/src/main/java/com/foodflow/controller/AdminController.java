package com.foodflow.controller;

import org.springdoc.core.annotations.ParameterObject;
import com.foodflow.dto.admin.AdminRestaurantResponse;
import com.foodflow.dto.admin.RestaurantStatusRequest;
import com.foodflow.dto.admin.UserStatusRequest;
import com.foodflow.dto.analytics.AdminAnalyticsResponse;
import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.order.OrderSummaryResponse;
import com.foodflow.dto.user.UserResponse;
import com.foodflow.entity.OrderStatus;
import com.foodflow.entity.Role;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.AdminService;
import com.foodflow.service.AnalyticsService;
import com.foodflow.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform administration. /api/admin/** requires ADMIN at the URL level (SecurityConfig). */
@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AnalyticsService analyticsService;
    private final AdminService adminService;
    private final OrderService orderService;

    @Operation(summary = "Platform analytics: users, restaurants, orders, revenue, last 7 days")
    @GetMapping("/analytics")
    public AdminAnalyticsResponse analytics() {
        return analyticsService.adminAnalytics();
    }

    /** ?role=CUSTOMER&enabled=false&keyword=asha */
    @Operation(summary = "Search users by role, status, name or email")
    @GetMapping("/users")
    public PageResponse<UserResponse> users(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) @Size(max = 100, message = "keyword must be at most 100 characters") String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return adminService.getUsers(role, enabled, keyword, pageable);
    }

    @Operation(summary = "Enable or disable a user; takes effect on their next request")
    @PatchMapping("/users/{id}/status")
    public UserResponse setUserStatus(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                      @Valid @RequestBody UserStatusRequest request) {
        return adminService.setUserEnabled(me.getId(), id, request.enabled());
    }

    /** Includes deactivated restaurants (unlike the public search). */
    @Operation(summary = "All restaurants, including deactivated ones")
    @GetMapping("/restaurants")
    public PageResponse<AdminRestaurantResponse> restaurants(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @Size(max = 100, message = "keyword must be at most 100 characters") String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return adminService.getRestaurants(active, keyword, pageable);
    }

    @Operation(summary = "Activate or deactivate a restaurant")
    @PatchMapping("/restaurants/{id}/status")
    public AdminRestaurantResponse setRestaurantStatus(@PathVariable Long id,
                                                       @Valid @RequestBody RestaurantStatusRequest request) {
        return adminService.setRestaurantActive(id, request.active());
    }

    @Operation(summary = "All orders on the platform")
    @GetMapping("/orders")
    public PageResponse<OrderSummaryResponse> orders(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getAllOrders(restaurantId, status, pageable);
    }
}
