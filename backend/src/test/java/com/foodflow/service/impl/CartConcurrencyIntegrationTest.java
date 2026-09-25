package com.foodflow.service.impl;

import com.foodflow.TestcontainersConfiguration;
import com.foodflow.dto.cart.CartItemRequest;
import com.foodflow.dto.cart.CartResponse;
import com.foodflow.entity.Category;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.exception.ConflictException;
import com.foodflow.repository.CategoryRepository;
import com.foodflow.repository.MenuItemRepository;
import com.foodflow.repository.RestaurantRepository;
import com.foodflow.repository.UserRepository;
import com.foodflow.service.CartService;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the row lock (SELECT ... FOR UPDATE) protects the one-restaurant rule under concurrency.
 *
 * <p>Two requests for the same, still EMPTY cart arrive at the same moment, each adding a dish
 * from a different restaurant. Without the lock, both could read "cart is empty", both pass the
 * check, and the cart would end up mixed. With the lock, the second waits, then sees the first
 * one's restaurant and is rejected.
 *
 * <p>NOT {@code @Transactional}: each thread must run and COMMIT its own real transaction, as
 * two HTTP requests would. The data is therefore committed and must be deleted in @AfterEach.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CartConcurrencyIntegrationTest {

    @Autowired private CartService cartService;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbc;

    private User customer;
    private User owner;
    private MenuItem biryani;
    private MenuItem pizza;

    @BeforeEach
    void setUp() {
        customer = userRepository.save(new User("C", "race-customer@example.com", "h", null, Role.CUSTOMER));
        owner = userRepository.save(new User("O", "race-owner@example.com", "h", null, Role.RESTAURANT_OWNER));
        Category category = categoryRepository.findByNameIgnoreCase("Biryani").orElseThrow();
        Restaurant a = restaurantRepository.save(new Restaurant(owner, "Race A", null, "1 Road", "9000000001", null));
        Restaurant b = restaurantRepository.save(new Restaurant(owner, "Race B", null, "2 Road", "9000000002", null));
        biryani = menuItemRepository.save(new MenuItem(a, category, "Race Biryani", null, new BigDecimal("250.00"), null));
        pizza = menuItemRepository.save(new MenuItem(b, category, "Race Pizza", null, new BigDecimal("199.00"), null));
    }

    @AfterEach
    void cleanUp() {
        // Restaurants cascade to menu items and cart lines; users cascade to carts.
        jdbc.update("delete from restaurants where owner_id = ?", owner.getId());
        jdbc.update("delete from users where id in (?, ?)", customer.getId(), owner.getId());
    }

    @RepeatedTest(5)
    void concurrentAddsFromTwoRestaurantsNeverProduceAMixedCart() throws Exception {
        // The cart row must already EXIST (empty), as after "clear cart". For a brand-new cart,
        // the second INSERT ... ON CONFLICT waits for the first transaction's uncommitted insert
        // on the unique index, which serialises the requests by accident and hides the race.
        cartService.addItem(customer.getId(), new CartItemRequest(biryani.getId(), 1));
        cartService.clear(customer.getId());

        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<CartResponse>> results = new ArrayList<>();
            for (MenuItem dish : List.of(biryani, pizza)) {
                Callable<CartResponse> call = () -> {
                    startTogether.await();
                    return cartService.addItem(customer.getId(), new CartItemRequest(dish.getId(), 1));
                };
                results.add(pool.submit(call));
            }
            startTogether.countDown();

            int succeeded = 0;
            int rejected = 0;
            for (Future<CartResponse> result : results) {
                try {
                    result.get(20, TimeUnit.SECONDS);
                    succeeded++;
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(ConflictException.class);
                    assertThat(((ConflictException) e.getCause()).getErrorCode()).isEqualTo("CART_RESTAURANT_MISMATCH");
                    rejected++;
                }
            }

            assertThat(succeeded).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
            CartResponse cart = cartService.getCart(customer.getId());
            assertThat(cart.items()).hasSize(1);
        } finally {
            pool.shutdownNow();
            cartService.clear(customer.getId());
        }
    }
}
