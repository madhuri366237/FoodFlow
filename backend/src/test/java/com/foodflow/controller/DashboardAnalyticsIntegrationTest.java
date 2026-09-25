package com.foodflow.controller;

import com.foodflow.ApiIntegrationTestBase;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Order;
import com.foodflow.entity.OrderStatus;
import com.foodflow.entity.PaymentMethod;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks every dashboard number against a fixture whose answers are worked out by hand.
 *
 *  #  customer  restaurant (owner)   items            method  status      paid?  when
 *  1  c1        Biryani Blues (A)    2 x 250 = 500    COD     DELIVERED   yes    today
 *  2  c1        Biryani Blues (A)    1 x 250 = 250    CARD    CONFIRMED   yes    today
 *  3  c2        Biryani Blues (A)    1 x  40 =  40    UPI     PLACED      no     today
 *  4  c2        Pizza Point   (A)    1 x 199 = 199    COD     CANCELLED   no     today
 *  5  c2        Dosa Corner   (B)    1 x  90 =  90    COD     DELIVERED   yes    today
 *  6  c1        Biryani Blues (A)    1 x 250 = 250    COD     DELIVERED   yes    3 days ago
 *
 * Revenue counts PAID orders only: platform 500+250+90+250 = 1090.
 */
class DashboardAnalyticsIntegrationTest extends ApiIntegrationTestBase {

    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private User ownerA;
    private User ownerB;
    private User c1;
    private User admin;
    private Restaurant biryaniBlues;
    private Restaurant pizzaPoint;
    private Restaurant dosaCorner;

    @BeforeEach
    void setUp() {
        ownerA = createUser("owner-a@example.com", Role.RESTAURANT_OWNER);
        ownerB = createUser("owner-b@example.com", Role.RESTAURANT_OWNER);
        c1 = createUser("c1@example.com", Role.CUSTOMER);
        User c2 = createUser("c2@example.com", Role.CUSTOMER);
        admin = createUser("admin@example.com", Role.ADMIN);

        biryaniBlues = createRestaurant(ownerA, "Biryani Blues", "4.5", true);
        pizzaPoint = createRestaurant(ownerA, "Pizza Point", "4.0", true);
        dosaCorner = createRestaurant(ownerB, "Dosa Corner", "4.2", true);
        MenuItem biryani = createMenuItem(biryaniBlues, "Biryani", "Chicken Biryani", "250.00", true);
        MenuItem raita = createMenuItem(biryaniBlues, "Starters", "Raita", "40.00", true);
        MenuItem pizza = createMenuItem(pizzaPoint, "Pizza", "Veg Pizza", "199.00", true);
        MenuItem dosa = createMenuItem(dosaCorner, "South Indian", "Masala Dosa", "90.00", true);

        order(c1, biryani, 2, PaymentMethod.CASH_ON_DELIVERY, OrderStatus.DELIVERED, true);
        order(c1, biryani, 1, PaymentMethod.CARD, OrderStatus.CONFIRMED, true);
        order(c2, raita, 1, PaymentMethod.UPI, OrderStatus.PLACED, false);
        order(c2, pizza, 1, PaymentMethod.CASH_ON_DELIVERY, OrderStatus.CANCELLED, false);
        order(c2, dosa, 1, PaymentMethod.CASH_ON_DELIVERY, OrderStatus.DELIVERED, true);
        Order old = order(c1, biryani, 1, PaymentMethod.CASH_ON_DELIVERY, OrderStatus.DELIVERED, true);

        entityManager.flush();
        jdbc.update("update orders set created_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(3))), old.getId());
        entityManager.clear();
    }

    /** Walks the real state machine up to the target status. */
    private Order order(User customer, MenuItem dish, int quantity, PaymentMethod method, OrderStatus target, boolean paid) {
        Order order = new Order(customer, dish.getRestaurant(), null, "Home", method);
        order.addItem(dish, quantity);
        if (paid) {
            order.markPaid();
        }
        if (target == OrderStatus.CANCELLED) {
            order.changeStatus(OrderStatus.CANCELLED, customer, null);
        } else {
            for (OrderStatus next : new OrderStatus[]{OrderStatus.CONFIRMED, OrderStatus.PREPARING,
                    OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED}) {
                if (order.getStatus() == target) break;
                order.changeStatus(next, customer, null);
            }
        }
        return orderRepository.save(order);
    }

    private ResultActions getAs(User user, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    // ---------------- admin ----------------

    @Test
    void adminAnalyticsCoverTheWholePlatform() throws Exception {
        getAs(admin, "/api/admin/analytics")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.total").value(5))
                .andExpect(jsonPath("$.users.customers").value(2))
                .andExpect(jsonPath("$.users.owners").value(2))
                .andExpect(jsonPath("$.users.admins").value(1))
                .andExpect(jsonPath("$.restaurants.total").value(3))
                .andExpect(jsonPath("$.orders.totalOrders").value(6))
                .andExpect(jsonPath("$.orders.todayOrders").value(5))
                .andExpect(jsonPath("$.orders.activeOrders").value(2))
                .andExpect(jsonPath("$.orders.deliveredOrders").value(3))
                .andExpect(jsonPath("$.orders.cancelledOrders").value(1))
                .andExpect(jsonPath("$.orders.paidOrders").value(4))
                .andExpect(jsonPath("$.orders.revenue").value(1090.00))
                .andExpect(jsonPath("$.orders.todayRevenue").value(840.00))
                .andExpect(jsonPath("$.orders.averageOrderValue").value(272.50))
                .andExpect(jsonPath("$.ordersByStatus.DELIVERED").value(3))
                .andExpect(jsonPath("$.ordersByStatus.PREPARING").value(0))  // zero rows still reported
                .andExpect(jsonPath("$.last7Days", hasSize(7)))
                .andExpect(jsonPath("$.last7Days[6].orders").value(5))       // today
                .andExpect(jsonPath("$.last7Days[6].revenue").value(840.00))
                .andExpect(jsonPath("$.last7Days[3].orders").value(1))       // 3 days ago
                .andExpect(jsonPath("$.last7Days[3].revenue").value(250.00))
                .andExpect(jsonPath("$.topRestaurants[0].name").value("Biryani Blues"))
                .andExpect(jsonPath("$.topRestaurants[0].revenue").value(1000.00));
    }

    @Test
    void onlyAdminsSeePlatformAnalytics() throws Exception {
        getAs(ownerA, "/api/admin/analytics").andExpect(status().isForbidden());
        getAs(c1, "/api/admin/analytics").andExpect(status().isForbidden());
    }

    // ---------------- owner ----------------

    @Test
    void ownerDashboardIsLimitedToOwnRestaurants() throws Exception {
        // Owner A: orders 1,2,3,4,6. Paid: 1,2,6 = 500+250+250 = 1000. Owner B's dosa never counts.
        getAs(ownerA, "/api/owner/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.totalOrders").value(5))
                .andExpect(jsonPath("$.orders.todayOrders").value(4))
                .andExpect(jsonPath("$.orders.activeOrders").value(2))
                .andExpect(jsonPath("$.orders.deliveredOrders").value(2))
                .andExpect(jsonPath("$.orders.cancelledOrders").value(1))
                .andExpect(jsonPath("$.orders.revenue").value(1000.00))
                .andExpect(jsonPath("$.orders.todayRevenue").value(750.00))
                // Best seller among PAID orders: 2 + 1 + 1 biryanis; the unpaid raita is not a sale.
                .andExpect(jsonPath("$.topDishes", hasSize(1)))
                .andExpect(jsonPath("$.topDishes[0].name").value("Chicken Biryani"))
                .andExpect(jsonPath("$.topDishes[0].quantity").value(4))
                .andExpect(jsonPath("$.restaurants[*].name").value(org.hamcrest.Matchers.containsInAnyOrder("Biryani Blues", "Pizza Point")));

        getAs(ownerB, "/api/owner/dashboard")
                .andExpect(jsonPath("$.orders.totalOrders").value(1))
                .andExpect(jsonPath("$.orders.revenue").value(90.00));
    }

    @Test
    void ownerCanFocusOnOneRestaurantButNotSomeoneElses() throws Exception {
        getAs(ownerA, "/api/owner/dashboard?restaurantId=" + pizzaPoint.getId())
                .andExpect(jsonPath("$.orders.totalOrders").value(1))
                .andExpect(jsonPath("$.orders.cancelledOrders").value(1))
                .andExpect(jsonPath("$.orders.revenue").value(0));

        getAs(ownerA, "/api/owner/dashboard?restaurantId=" + dosaCorner.getId()).andExpect(status().isForbidden());
    }

    // ---------------- customer ----------------

    @Test
    void customerDashboardShowsOwnHistory() throws Exception {
        // c1: orders 1, 2, 6, all paid: 500 + 250 + 250.
        getAs(c1, "/api/users/me/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(3))
                .andExpect(jsonPath("$.activeOrders").value(1))
                .andExpect(jsonPath("$.deliveredOrders").value(2))
                .andExpect(jsonPath("$.totalSpent").value(1000.00))
                .andExpect(jsonPath("$.favoriteRestaurant.name").value("Biryani Blues"))
                .andExpect(jsonPath("$.favoriteRestaurant.orders").value(3))
                .andExpect(jsonPath("$.recentOrders", hasSize(3)));
    }

    @Test
    void customerDashboardIsCustomerOnly() throws Exception {
        getAs(ownerA, "/api/users/me/dashboard").andExpect(status().isForbidden());
    }

    @Test
    void newCustomerGetsZerosNotErrors() throws Exception {
        User fresh = createUser("fresh@example.com", Role.CUSTOMER);

        getAs(fresh, "/api/users/me/dashboard")
                .andExpect(jsonPath("$.totalOrders").value(0))
                .andExpect(jsonPath("$.totalSpent").value(0))
                .andExpect(jsonPath("$.favoriteRestaurant").doesNotExist())
                .andExpect(jsonPath("$.recentOrders", hasSize(0)));
    }
}
