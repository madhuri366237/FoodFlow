package com.foodflow.repository;

import com.foodflow.dto.analytics.DailyPoint;
import com.foodflow.dto.analytics.OrderStats;
import com.foodflow.dto.analytics.TopDish;
import com.foodflow.dto.analytics.TopRestaurant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reporting queries in plain SQL.
 *
 * <p>JPA is built for loading and saving individual entities (OLTP). Dashboards need
 * aggregation over many rows: COUNT/SUM with FILTER, GROUP BY, date bucketing in a time zone.
 * That is exactly what SQL is for, and running it in PostgreSQL means only a handful of numbers
 * travel to the JVM instead of thousands of Order entities.
 *
 * <p>Every query runs on "orders o JOIN restaurants r" plus a {@link Scope}: nothing (admin),
 * the owner's restaurants, or one customer. The same query code therefore serves all three
 * dashboards, and a caller can never see another owner's or customer's numbers.
 * All values are bind parameters (":name"), never string concatenation, so no SQL injection.
 */
@Repository
@RequiredArgsConstructor
public class AnalyticsQueries {

    // "Revenue" = money actually received (see OrderStats).
    private static final String PAID = "o.payment_status = 'PAID'";

    private final NamedParameterJdbcTemplate jdbc;

    /** A WHERE fragment + its parameters. Built only from constants below, never from user input. */
    public record Scope(String where, Map<String, Object> params) {

        public static Scope platform() {
            return new Scope("TRUE", Map.of());
        }

        public static Scope owner(Long ownerId, Long restaurantId) {
            if (restaurantId == null) {
                return new Scope("r.owner_id = :ownerId", Map.of("ownerId", ownerId));
            }
            return new Scope("r.owner_id = :ownerId AND r.id = :restaurantId",
                    Map.of("ownerId", ownerId, "restaurantId", restaurantId));
        }

        public static Scope customer(Long userId) {
            return new Scope("o.user_id = :userId", Map.of("userId", userId));
        }

        MapSqlParameterSource toParams() {
            return new MapSqlParameterSource(params);
        }
    }

    /*
     * One pass over the rows computes every figure: PostgreSQL's aggregate FILTER clause counts
     * or sums only the rows matching its condition, instead of running 8 separate queries.
     */
    public OrderStats orderStats(Scope scope, Instant todayStart) {
        String sql = """
                SELECT count(*)                                                   AS total_orders,
                       count(*) FILTER (WHERE o.created_at >= :todayStart)         AS today_orders,
                       count(*) FILTER (WHERE o.status NOT IN ('DELIVERED', 'CANCELLED')) AS active_orders,
                       count(*) FILTER (WHERE o.status = 'DELIVERED')              AS delivered_orders,
                       count(*) FILTER (WHERE o.status = 'CANCELLED')              AS cancelled_orders,
                       count(*) FILTER (WHERE %1$s)                                AS paid_orders,
                       coalesce(sum(o.total_amount) FILTER (WHERE %1$s), 0)        AS revenue,
                       coalesce(sum(o.total_amount) FILTER (WHERE %1$s AND o.created_at >= :todayStart), 0) AS today_revenue,
                       coalesce(sum(o.discount_amount) FILTER (WHERE %1$s), 0)     AS discounts
                FROM orders o JOIN restaurants r ON r.id = o.restaurant_id
                WHERE %2$s
                """.formatted(PAID, scope.where());

        return jdbc.queryForObject(sql, scope.toParams().addValue("todayStart", Timestamp.from(todayStart)), (rs, row) -> {
            long paid = rs.getLong("paid_orders");
            BigDecimal revenue = rs.getBigDecimal("revenue").setScale(2, RoundingMode.HALF_UP);
            BigDecimal average = paid == 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : revenue.divide(BigDecimal.valueOf(paid), 2, RoundingMode.HALF_UP);
            return new OrderStats(rs.getLong("total_orders"), rs.getLong("today_orders"), rs.getLong("active_orders"),
                    rs.getLong("delivered_orders"), rs.getLong("cancelled_orders"), paid, revenue,
                    rs.getBigDecimal("today_revenue").setScale(2, RoundingMode.HALF_UP), average,
                    rs.getBigDecimal("discounts").setScale(2, RoundingMode.HALF_UP));
        });
    }

    /** {"PLACED": 3, "DELIVERED": 10, ...}: every status is present, with 0 if there are none. */
    public Map<String, Long> ordersByStatus(Scope scope, List<String> allStatuses) {
        String sql = """
                SELECT o.status, count(*) AS n
                FROM orders o JOIN restaurants r ON r.id = o.restaurant_id
                WHERE %s
                GROUP BY o.status
                """.formatted(scope.where());
        Map<String, Long> result = new LinkedHashMap<>();
        allStatuses.forEach(status -> result.put(status, 0L));
        jdbc.query(sql, scope.toParams(), rs -> {
            result.put(rs.getString("status"), rs.getLong("n"));
        });
        return result;
    }

    /*
     * Buckets by LOCAL calendar day: "created_at AT TIME ZONE 'Asia/Kolkata'" converts the stored
     * UTC instant to Indian local time before taking the date. An order at 01:00 IST on the 5th
     * (19:30 UTC on the 4th) belongs to the 5th. Days with no orders are filled with zeros in Java,
     * so the chart always has exactly `days` bars.
     */
    public List<DailyPoint> dailySeries(Scope scope, LocalDate firstDay, int days, ZoneId zone) {
        Instant from = firstDay.atStartOfDay(zone).toInstant();
        String sql = """
                SELECT (o.created_at AT TIME ZONE :zone)::date                 AS day,
                       count(*)                                                AS orders,
                       coalesce(sum(o.total_amount) FILTER (WHERE %s), 0)      AS revenue
                FROM orders o JOIN restaurants r ON r.id = o.restaurant_id
                WHERE %s AND o.created_at >= :from
                GROUP BY day
                """.formatted(PAID, scope.where());

        Map<LocalDate, DailyPoint> byDay = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = firstDay.plusDays(i);
            byDay.put(day, new DailyPoint(day, 0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
        }
        MapSqlParameterSource params = scope.toParams().addValue("zone", zone.getId()).addValue("from", Timestamp.from(from));
        jdbc.query(sql, params, rs -> {
            LocalDate day = rs.getDate("day").toLocalDate();
            if (byDay.containsKey(day)) {
                byDay.put(day, new DailyPoint(day, rs.getLong("orders"),
                        rs.getBigDecimal("revenue").setScale(2, RoundingMode.HALF_UP)));
            }
        });
        return List.copyOf(byDay.values());
    }

    /** Restaurants ranked by revenue, then by order count (cancelled orders excluded from counts). */
    public List<TopRestaurant> topRestaurants(Scope scope, int limit) {
        String sql = """
                SELECT r.id, r.name,
                       count(*) FILTER (WHERE o.status <> 'CANCELLED')         AS orders,
                       coalesce(sum(o.total_amount) FILTER (WHERE %s), 0)      AS revenue
                FROM orders o JOIN restaurants r ON r.id = o.restaurant_id
                WHERE %s
                GROUP BY r.id, r.name
                ORDER BY revenue DESC, orders DESC, r.name
                LIMIT :limit
                """.formatted(PAID, scope.where());
        return jdbc.query(sql, scope.toParams().addValue("limit", limit), (rs, row) -> new TopRestaurant(
                rs.getLong("id"), rs.getString("name"), rs.getLong("orders"),
                rs.getBigDecimal("revenue").setScale(2, RoundingMode.HALF_UP)));
    }

    /** Best sellers among PAID orders, by quantity sold. */
    public List<TopDish> topDishes(Scope scope, int limit) {
        String sql = """
                SELECT oi.item_name AS name, sum(oi.quantity) AS quantity, sum(oi.line_total) AS revenue
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                JOIN restaurants r ON r.id = o.restaurant_id
                WHERE %s AND %s
                GROUP BY oi.item_name
                ORDER BY quantity DESC, revenue DESC, oi.item_name
                LIMIT :limit
                """.formatted(PAID, scope.where());
        return jdbc.query(sql, scope.toParams().addValue("limit", limit), (rs, row) -> new TopDish(
                rs.getString("name"), rs.getLong("quantity"), rs.getBigDecimal("revenue").setScale(2, RoundingMode.HALF_UP)));
    }

    /** Platform-wide user counts in one GROUP BY. */
    public Map<String, long[]> usersByRole() {
        Map<String, long[]> result = new LinkedHashMap<>();
        jdbc.query("SELECT role, count(*) AS total, count(*) FILTER (WHERE NOT enabled) AS disabled FROM users GROUP BY role",
                Map.of(), rs -> {
                    result.put(rs.getString("role"), new long[]{rs.getLong("total"), rs.getLong("disabled")});
                });
        return result;
    }

    /** {total, active, open now}. */
    public long[] restaurantCounts() {
        return jdbc.queryForObject("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE active) AS active,
                       count(*) FILTER (WHERE active AND is_open) AS open_now
                FROM restaurants
                """, Map.of(), (rs, row) -> new long[]{rs.getLong("total"), rs.getLong("active"), rs.getLong("open_now")});
    }
}
