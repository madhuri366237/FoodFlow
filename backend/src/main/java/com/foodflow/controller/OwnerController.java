package com.foodflow.controller;

import org.springdoc.core.annotations.ParameterObject;
import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.menu.MenuItemResponse;
import com.foodflow.dto.analytics.OwnerDashboardResponse;
import com.foodflow.dto.order.OrderSummaryResponse;
import com.foodflow.service.AnalyticsService;
import com.foodflow.dto.restaurant.RestaurantResponse;
import com.foodflow.entity.OrderStatus;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.MenuItemService;
import com.foodflow.service.OrderService;
import com.foodflow.service.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The owner dashboard's read endpoints. /api/owner/** requires RESTAURANT_OWNER at the URL
 * level (SecurityConfig). These views include things customers never see: inactive
 * restaurants and unavailable dishes.
 */
@Tag(name = "Owner")
@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
public class OwnerController {

    private final RestaurantService restaurantService;
    private final MenuItemService menuItemService;
    private final OrderService orderService;
    private final AnalyticsService analyticsService;

    /** Analytics for all my restaurants, or one of them with ?restaurantId=. */
    @Operation(summary = "Owner analytics for all my restaurants, or one with ?restaurantId")
    @GetMapping("/dashboard")
    public OwnerDashboardResponse dashboard(@AuthenticationPrincipal UserPrincipal me,
                                            @RequestParam(required = false) Long restaurantId) {
        return analyticsService.ownerDashboard(me.getId(), restaurantId);
    }

    @Operation(summary = "My restaurants, including ones an admin deactivated")
    @GetMapping("/restaurants")
    public PageResponse<RestaurantResponse> myRestaurants(@AuthenticationPrincipal UserPrincipal me,
                                                          @ParameterObject @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return restaurantService.getOwnerRestaurants(me.getId(), pageable);
    }

    @Operation(summary = "Full menu of my restaurant, including sold-out dishes")
    @GetMapping("/restaurants/{id}/menu")
    public List<MenuItemResponse> fullMenu(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return menuItemService.getOwnerMenu(me.getId(), id);
    }

    /** Incoming orders of the caller's restaurants: ?status=PLACED&restaurantId=3, newest first. */
    @Operation(summary = "Incoming orders of my restaurants, filterable by status")
    @GetMapping("/orders")
    public PageResponse<OrderSummaryResponse> orders(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getOwnerOrders(me.getId(), restaurantId, status, pageable);
    }
}
