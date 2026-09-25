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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewApiIntegrationTest extends ApiIntegrationTestBase {

    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;

    private User customer;
    private User otherCustomer;
    private User owner;
    private Restaurant restaurant;
    private Restaurant otherRestaurant;
    private MenuItem biryani;

    @BeforeEach
    void setUp() {
        customer = createUser("customer@example.com", Role.CUSTOMER);
        otherCustomer = createUser("other@example.com", Role.CUSTOMER);
        owner = createUser("owner@example.com", Role.RESTAURANT_OWNER);
        restaurant = createRestaurant(owner, "Biryani Blues", "0.0", true);
        restaurant.updateRating(BigDecimal.ZERO, 0);
        otherRestaurant = createRestaurant(owner, "Pizza Point", "0.0", true);
        biryani = createMenuItem(restaurant, "Biryani", "Chicken Biryani", "250.00", true);
    }

    /** An order taken through the real state machine up to the given status. */
    private Order order(User who, OrderStatus upTo) {
        Order order = new Order(who, restaurant, null, "Home: 12 MG Road", PaymentMethod.CASH_ON_DELIVERY);
        order.addItem(biryani, 1);
        OrderStatus[] path = {OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP,
                OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED};
        if (upTo == OrderStatus.CANCELLED) {
            order.changeStatus(OrderStatus.CANCELLED, who, null);
        } else {
            for (OrderStatus next : path) {
                if (order.getStatus() == upTo) {
                    break;
                }
                order.changeStatus(next, owner, null);
            }
        }
        return orderRepository.save(order);
    }

    private ResultActions review(User who, Long restaurantId, Long orderId, Object rating, String comment) throws Exception {
        String body = comment == null
                ? "{\"orderId\":%d,\"rating\":%s}".formatted(orderId, rating)
                : "{\"orderId\":%d,\"rating\":%s,\"comment\":\"%s\"}".formatted(orderId, rating, comment);
        return mockMvc.perform(post("/api/restaurants/" + restaurantId + "/reviews")
                .header(HttpHeaders.AUTHORIZATION, bearer(who))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Restaurant reloaded() {
        entityManager.flush();
        entityManager.clear();
        return restaurantRepository.findById(restaurant.getId()).orElseThrow();
    }

    // ---------- happy path + rating calculation ----------

    @Test
    void customerReviewsDeliveredOrderAndRatingUpdates() throws Exception {
        Order delivered = order(customer, OrderStatus.DELIVERED);

        review(customer, restaurant.getId(), delivered.getId(), 5, "Amazing biryani")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Amazing biryani"))
                .andExpect(jsonPath("$.customerName").value("Test CUSTOMER"));

        Restaurant after = reloaded();
        assertThat(after.getRating()).isEqualByComparingTo("5.0");
        assertThat(after.getRatingCount()).isEqualTo(1);
    }

    @Test
    void averageIsExactAndRoundedHalfUpToOneDecimal() throws Exception {
        // 5, 4, 4 -> 13 / 3 = 4.333... -> 4.3
        review(customer, restaurant.getId(), order(customer, OrderStatus.DELIVERED).getId(), 5, null);
        review(customer, restaurant.getId(), order(customer, OrderStatus.DELIVERED).getId(), 4, null);
        review(otherCustomer, restaurant.getId(), order(otherCustomer, OrderStatus.DELIVERED).getId(), 4, null);
        assertThat(reloaded().getRating()).isEqualByComparingTo("4.3");

        // + 5 -> 18 / 4 = 4.5 exactly
        review(otherCustomer, restaurant.getId(), order(otherCustomer, OrderStatus.DELIVERED).getId(), 5, null);
        Restaurant after = reloaded();
        assertThat(after.getRating()).isEqualByComparingTo("4.5");
        assertThat(after.getRatingCount()).isEqualTo(4);

        // + 2 -> 20 / 5 = 4.0
        review(customer, restaurant.getId(), order(customer, OrderStatus.DELIVERED).getId(), 2, null);
        assertThat(reloaded().getRating()).isEqualByComparingTo("4.0");
    }

    @Test
    void ratingFeedsRestaurantSearchAndSorting() throws Exception {
        review(customer, restaurant.getId(), order(customer, OrderStatus.DELIVERED).getId(), 5, null);
        reloaded();

        mockMvc.perform(get("/api/restaurants?minRating=4.5"))
                .andExpect(jsonPath("$.content[*].name").value(contains("Biryani Blues")));
    }

    // ---------- the rules ----------

    @Test
    void orderMustBeDelivered() throws Exception {
        for (OrderStatus notYet : new OrderStatus[]{OrderStatus.PLACED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED}) {
            review(customer, restaurant.getId(), order(customer, notYet).getId(), 5, null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("ORDER_NOT_DELIVERED"));
        }
        assertThat(reloaded().getRatingCount()).isZero();
    }

    @Test
    void oneReviewPerOrder() throws Exception {
        Order delivered = order(customer, OrderStatus.DELIVERED);
        review(customer, restaurant.getId(), delivered.getId(), 5, null).andExpect(status().isCreated());

        review(customer, restaurant.getId(), delivered.getId(), 1, "changed my mind")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_REVIEWED"));

        Restaurant after = reloaded();
        assertThat(after.getRatingCount()).isEqualTo(1);
        assertThat(after.getRating()).isEqualByComparingTo("5.0");
    }

    @Test
    void cannotReviewWithSomeoneElsesOrder() throws Exception {
        Order theirs = order(otherCustomer, OrderStatus.DELIVERED);

        review(customer, restaurant.getId(), theirs.getId(), 1, null).andExpect(status().isNotFound());
    }

    @Test
    void orderMustBelongToTheReviewedRestaurant() throws Exception {
        Order delivered = order(customer, OrderStatus.DELIVERED);

        review(customer, otherRestaurant.getId(), delivered.getId(), 1, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Order " + delivered.getId() + " was not placed at this restaurant"));
    }

    @Test
    void onlyCustomersCanReview() throws Exception {
        Order delivered = order(customer, OrderStatus.DELIVERED);

        review(owner, restaurant.getId(), delivered.getId(), 5, null).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/restaurants/" + restaurant.getId() + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":1,\"rating\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ratingMustBeBetweenOneAndFive() throws Exception {
        Order delivered = order(customer, OrderStatus.DELIVERED);

        review(customer, restaurant.getId(), delivered.getId(), 0, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").value("rating must be between 1 and 5"));
        review(customer, restaurant.getId(), delivered.getId(), 6, null).andExpect(status().isBadRequest());
        // Jackson would silently truncate 4.5 to 4 without accept-float-as-int: false (application.yml).
        review(customer, restaurant.getId(), delivered.getId(), "4.5", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for rating"));
        review(customer, restaurant.getId(), delivered.getId(), 5, "x".repeat(1001)).andExpect(status().isBadRequest());
    }

    // ---------- reading ----------

    @Test
    void reviewsArePublicNewestFirstWithoutPrivateData() throws Exception {
        review(customer, restaurant.getId(), order(customer, OrderStatus.DELIVERED).getId(), 3, "ok");
        review(otherCustomer, restaurant.getId(), order(otherCustomer, OrderStatus.DELIVERED).getId(), 5, "great");

        mockMvc.perform(get("/api/restaurants/" + restaurant.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].comment").value(contains("great", "ok")))
                .andExpect(jsonPath("$.content[0].customerEmail").doesNotExist())
                .andExpect(jsonPath("$.content[0].orderId").doesNotExist());
    }

    @Test
    void orderDetailsSayWhetherItWasReviewed() throws Exception {
        Order delivered = order(customer, OrderStatus.DELIVERED);
        mockMvc.perform(get("/api/orders/" + delivered.getId()).header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(jsonPath("$.reviewed").value(false));

        review(customer, restaurant.getId(), delivered.getId(), 4, null);

        mockMvc.perform(get("/api/orders/" + delivered.getId()).header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(jsonPath("$.reviewed").value(true));
    }
}
