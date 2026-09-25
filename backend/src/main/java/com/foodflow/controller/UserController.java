package com.foodflow.controller;

import com.foodflow.dto.analytics.CustomerDashboardResponse;
import com.foodflow.dto.user.UserResponse;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.AnalyticsService;
import com.foodflow.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AnalyticsService analyticsService;

    /*
     * "/me" rather than "/{id}": the user id comes from the verified token, never from the URL,
     * so there is no id parameter a caller could change to read someone else's profile.
     */
    @Operation(summary = "Current user's profile")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal me) {
        return userService.getCurrentUser(me.getId());
    }

    /** The customer's own dashboard: order counts, total spent and saved, favourite restaurant. */
    @Operation(summary = "Customer dashboard: order counts, total spent and saved, favourite restaurant")
    @GetMapping("/me/dashboard")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CustomerDashboardResponse dashboard(@AuthenticationPrincipal UserPrincipal me) {
        return analyticsService.customerDashboard(me.getId());
    }
}
