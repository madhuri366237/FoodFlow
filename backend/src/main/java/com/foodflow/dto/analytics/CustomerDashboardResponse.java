package com.foodflow.dto.analytics;

import com.foodflow.dto.order.OrderSummaryResponse;

import java.math.BigDecimal;
import java.util.List;

public record CustomerDashboardResponse(
        long totalOrders,
        long activeOrders,
        long deliveredOrders,
        BigDecimal totalSpent,
        BigDecimal totalSaved,          // coupon discounts on paid orders
        TopRestaurant favoriteRestaurant,
        List<OrderSummaryResponse> recentOrders) {
}
