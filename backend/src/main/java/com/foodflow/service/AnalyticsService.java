package com.foodflow.service;

import com.foodflow.dto.analytics.AdminAnalyticsResponse;
import com.foodflow.dto.analytics.CustomerDashboardResponse;
import com.foodflow.dto.analytics.OwnerDashboardResponse;

public interface AnalyticsService {

    AdminAnalyticsResponse adminAnalytics();

    /** restaurantId is optional: null = all of the owner's restaurants. */
    OwnerDashboardResponse ownerDashboard(Long ownerId, Long restaurantId);

    CustomerDashboardResponse customerDashboard(Long customerId);
}
