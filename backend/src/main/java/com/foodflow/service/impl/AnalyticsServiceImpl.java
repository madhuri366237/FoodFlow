package com.foodflow.service.impl;

import com.foodflow.dto.analytics.AdminAnalyticsResponse;
import com.foodflow.dto.analytics.CustomerDashboardResponse;
import com.foodflow.dto.analytics.OrderStats;
import com.foodflow.dto.analytics.OwnerDashboardResponse;
import com.foodflow.dto.analytics.TopRestaurant;
import com.foodflow.dto.order.OrderSummaryResponse;
import com.foodflow.entity.OrderStatus;
import com.foodflow.entity.Role;
import com.foodflow.repository.AnalyticsQueries;
import com.foodflow.repository.AnalyticsQueries.Scope;
import com.foodflow.repository.OrderRepository;
import com.foodflow.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/*
 * REPEATABLE_READ: a dashboard runs several queries. Under PostgreSQL's default READ COMMITTED,
 * EACH statement sees a fresh snapshot, so an order committed between two queries could appear
 * in "total orders" but not in "revenue". REPEATABLE READ gives the whole transaction ONE
 * snapshot, so all figures agree with each other. Read-only, it never causes serialization failures.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final int CHART_DAYS = 7;
    private static final List<String> STATUSES = Arrays.stream(OrderStatus.values()).map(Enum::name).toList();

    private final AnalyticsQueries queries;
    private final OrderRepository orderRepository;
    private final RestaurantAccess restaurantAccess;
    private final Clock clock;

    @Value("${app.timezone:Asia/Kolkata}")
    private String timezone;

    @Override
    public AdminAnalyticsResponse adminAnalytics() {
        Scope scope = Scope.platform();

        Map<String, long[]> byRole = queries.usersByRole();
        long customers = count(byRole, Role.CUSTOMER, 0);
        long owners = count(byRole, Role.RESTAURANT_OWNER, 0);
        long admins = count(byRole, Role.ADMIN, 0);
        long disabled = byRole.values().stream().mapToLong(values -> values[1]).sum();
        long[] restaurants = queries.restaurantCounts();

        return new AdminAnalyticsResponse(
                new AdminAnalyticsResponse.UserStats(customers + owners + admins, customers, owners, admins, disabled),
                new AdminAnalyticsResponse.RestaurantStats(restaurants[0], restaurants[1], restaurants[2]),
                queries.orderStats(scope, todayStart()),
                queries.ordersByStatus(scope, STATUSES),
                queries.dailySeries(scope, firstChartDay(), CHART_DAYS, zone()),
                queries.topRestaurants(scope, 5));
    }

    @Override
    public OwnerDashboardResponse ownerDashboard(Long ownerId, Long restaurantId) {
        if (restaurantId != null) {
            restaurantAccess.requireOwnedRestaurant(restaurantId, ownerId); // 404 / 403 for someone else's
        }
        Scope scope = Scope.owner(ownerId, restaurantId);
        return new OwnerDashboardResponse(
                restaurantId,
                queries.orderStats(scope, todayStart()),
                queries.ordersByStatus(scope, STATUSES),
                queries.dailySeries(scope, firstChartDay(), CHART_DAYS, zone()),
                queries.topDishes(scope, 5),
                queries.topRestaurants(Scope.owner(ownerId, null), 20));
    }

    @Override
    public CustomerDashboardResponse customerDashboard(Long customerId) {
        Scope scope = Scope.customer(customerId);
        OrderStats stats = queries.orderStats(scope, todayStart());
        // "Favourite" = where they order most often.
        TopRestaurant favourite = queries.topRestaurants(scope, 20).stream()
                .filter(restaurant -> restaurant.orders() > 0)
                .max((a, b) -> Long.compare(a.orders(), b.orders()))
                .orElse(null);
        List<OrderSummaryResponse> recent = orderRepository
                .findByCustomerId(customerId, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(OrderSummaryResponse::from)
                .getContent();

        return new CustomerDashboardResponse(stats.totalOrders(), stats.activeOrders(), stats.deliveredOrders(),
                stats.revenue(), stats.discountsGiven(), favourite, recent);
    }

    private static long count(Map<String, long[]> byRole, Role role, int index) {
        long[] values = byRole.get(role.name());
        return values == null ? 0 : values[index];
    }

    private ZoneId zone() {
        return ZoneId.of(timezone);
    }

    /** Midnight today in the business time zone, as an instant (what created_at is compared with). */
    private Instant todayStart() {
        return LocalDate.now(clock.withZone(zone())).atStartOfDay(zone()).toInstant();
    }

    private LocalDate firstChartDay() {
        return LocalDate.now(clock.withZone(zone())).minusDays(CHART_DAYS - 1);
    }
}
