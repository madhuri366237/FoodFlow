package com.foodflow.dto.analytics;

import java.util.List;
import java.util.Map;

/** Figures for all of an owner's restaurants, or one of them if restaurantId was given. */
public record OwnerDashboardResponse(
        Long restaurantId,
        OrderStats orders,
        Map<String, Long> ordersByStatus,
        List<DailyPoint> last7Days,
        List<TopDish> topDishes,
        List<TopRestaurant> restaurants) {
}
