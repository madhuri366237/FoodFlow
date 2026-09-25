package com.foodflow.dto.analytics;

import java.util.List;
import java.util.Map;

public record AdminAnalyticsResponse(
        UserStats users,
        RestaurantStats restaurants,
        OrderStats orders,
        Map<String, Long> ordersByStatus,
        List<DailyPoint> last7Days,
        List<TopRestaurant> topRestaurants) {

    public record UserStats(long total, long customers, long owners, long admins, long disabled) {
    }

    public record RestaurantStats(long total, long active, long openNow) {
    }
}
