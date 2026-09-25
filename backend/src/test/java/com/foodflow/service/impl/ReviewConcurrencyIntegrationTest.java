package com.foodflow.service.impl;

import com.foodflow.TestcontainersConfiguration;
import com.foodflow.dto.review.ReviewRequest;
import com.foodflow.entity.Category;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Order;
import com.foodflow.entity.OrderStatus;
import com.foodflow.entity.PaymentMethod;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.repository.CategoryRepository;
import com.foodflow.repository.MenuItemRepository;
import com.foodflow.repository.OrderRepository;
import com.foodflow.repository.RestaurantRepository;
import com.foodflow.repository.UserRepository;
import com.foodflow.service.ReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 6 customers submit reviews for the same restaurant at the same moment.
 * The cached rating must count ALL of them: rating_count = 6 and the exact average.
 * Real commits (not @Transactional), so the transactions genuinely overlap.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReviewConcurrencyIntegrationTest {

    private static final int[] RATINGS = {5, 4, 3, 5, 2, 5}; // sum 24 / 6 = 4.0

    @Autowired private ReviewService reviewService;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbc;

    private final List<User> customers = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private User owner;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("O", "review-race-owner@example.com", "h", null, Role.RESTAURANT_OWNER));
        Category category = categoryRepository.findByNameIgnoreCase("Biryani").orElseThrow();
        restaurant = restaurantRepository.save(new Restaurant(owner, "Review Race Cafe", null, "1 Road", "9000000001", null));
        MenuItem dish = menuItemRepository.save(new MenuItem(restaurant, category, "Race Biryani", null, new BigDecimal("250.00"), null));

        for (int i = 0; i < RATINGS.length; i++) {
            User customer = userRepository.save(new User("C" + i, "review-race-" + i + "@example.com", "h", null, Role.CUSTOMER));
            customers.add(customer);
            Order order = new Order(customer, restaurant, null, "Home", PaymentMethod.CASH_ON_DELIVERY);
            order.addItem(dish, 1);
            for (OrderStatus next : new OrderStatus[]{OrderStatus.CONFIRMED, OrderStatus.PREPARING,
                    OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED}) {
                order.changeStatus(next, owner, null);
            }
            orders.add(orderRepository.save(order));
        }
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from reviews where restaurant_id = ?", restaurant.getId());
        jdbc.update("delete from orders where restaurant_id = ?", restaurant.getId());
        jdbc.update("delete from restaurants where id = ?", restaurant.getId());
        for (User customer : customers) {
            jdbc.update("delete from users where id = ?", customer.getId());
        }
        jdbc.update("delete from users where id = ?", owner.getId());
    }

    @RepeatedTest(3)
    void concurrentReviewsAreAllCountedInTheAverage() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(RATINGS.length);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < RATINGS.length; i++) {
                Long customerId = customers.get(i).getId();
                Long orderId = orders.get(i).getId();
                int rating = RATINGS[i];
                results.add(pool.submit(() -> {
                    start.await();
                    return reviewService.create(customerId, restaurant.getId(), new ReviewRequest(orderId, rating, null));
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS); // every review must succeed
            }

            Map<String, Object> row = jdbc.queryForMap(
                    "select rating, rating_count from restaurants where id = ?", restaurant.getId());
            assertThat(row.get("rating_count")).isEqualTo(RATINGS.length);
            assertThat((BigDecimal) row.get("rating")).isEqualByComparingTo("4.0");
        } finally {
            pool.shutdownNow();
            jdbc.update("delete from reviews where restaurant_id = ?", restaurant.getId());
            jdbc.update("update restaurants set rating = 0, rating_count = 0 where id = ?", restaurant.getId());
        }
    }
}
