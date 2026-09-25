package com.foodflow.service.impl;

import com.foodflow.TestcontainersConfiguration;
import com.foodflow.dto.cart.CartItemRequest;
import com.foodflow.dto.order.CreateOrderRequest;
import com.foodflow.entity.Address;
import com.foodflow.entity.Category;
import com.foodflow.entity.Coupon;
import com.foodflow.entity.DiscountType;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.PaymentMethod;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.exception.InvalidCouponException;
import com.foodflow.repository.AddressRepository;
import com.foodflow.repository.CategoryRepository;
import com.foodflow.repository.CouponRepository;
import com.foodflow.repository.MenuItemRepository;
import com.foodflow.repository.RestaurantRepository;
import com.foodflow.repository.UserRepository;
import com.foodflow.service.CartService;
import com.foodflow.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 8 customers check out AT THE SAME MOMENT with a coupon that has 3 uses left.
 * Exactly 3 must get it and 5 must be refused; used_count must end at exactly 3.
 * Real commits (not @Transactional), so the concurrent transactions are genuine.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CouponConcurrencyIntegrationTest {

    private static final int CUSTOMERS = 8;
    private static final int USES = 3;

    @Autowired private OrderService orderService;
    @Autowired private CartService cartService;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private JdbcTemplate jdbc;

    private final List<User> customers = new ArrayList<>();
    private final List<Address> addresses = new ArrayList<>();
    private User owner;
    private Coupon coupon;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("O", "race-coupon-owner@example.com", "h", null, Role.RESTAURANT_OWNER));
        Category category = categoryRepository.findByNameIgnoreCase("Biryani").orElseThrow();
        Restaurant restaurant = restaurantRepository.save(new Restaurant(owner, "Coupon Cafe", null, "1 Road", "9000000001", null));
        MenuItem dish = menuItemRepository.save(new MenuItem(restaurant, category, "Coupon Biryani", null, new BigDecimal("250.00"), null));

        Coupon limited = new Coupon("RACE3", DiscountType.FIXED_AMOUNT, new BigDecimal("50"), Instant.now().plus(Duration.ofDays(1)));
        limited.setUsageLimit(USES);
        coupon = couponRepository.save(limited);

        for (int i = 0; i < CUSTOMERS; i++) {
            User customer = userRepository.save(new User("C" + i, "race-coupon-" + i + "@example.com", "h", null, Role.CUSTOMER));
            customers.add(customer);
            addresses.add(addressRepository.save(new Address(customer, "Home", "1 Road", null, "Bengaluru", "Karnataka", "560001", true)));
            cartService.addItem(customer.getId(), new CartItemRequest(dish.getId(), 1));
        }
    }

    @AfterEach
    void cleanUp() {
        List<Long> ids = customers.stream().map(User::getId).toList();
        for (Long id : ids) {
            jdbc.update("delete from payments where order_id in (select id from orders where user_id = ?)", id);
            jdbc.update("delete from orders where user_id = ?", id);
            jdbc.update("delete from users where id = ?", id);
        }
        jdbc.update("delete from coupons where id = ?", coupon.getId());
        jdbc.update("delete from restaurants where owner_id = ?", owner.getId());
        jdbc.update("delete from users where id = ?", owner.getId());
    }

    @Test
    void couponIsNeverRedeemedMoreThanItsLimit() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CUSTOMERS);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < CUSTOMERS; i++) {
                Long customerId = customers.get(i).getId();
                Long addressId = addresses.get(i).getId();
                results.add(pool.submit(() -> {
                    start.await();
                    return orderService.placeOrder(customerId,
                            new CreateOrderRequest(addressId, PaymentMethod.CASH_ON_DELIVERY, "RACE3"));
                }));
            }
            start.countDown();

            int succeeded = 0;
            int refused = 0;
            for (Future<?> result : results) {
                try {
                    result.get(30, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(InvalidCouponException.class);
                    refused++;
                }
            }

            assertThat(succeeded).isEqualTo(USES);
            assertThat(refused).isEqualTo(CUSTOMERS - USES);
            assertThat(jdbc.queryForObject("select used_count from coupons where id = ?", Integer.class, coupon.getId()))
                    .isEqualTo(USES);
            assertThat(jdbc.queryForObject("select count(*) from orders where coupon_id = ?", Integer.class, coupon.getId()))
                    .isEqualTo(USES);
        } finally {
            pool.shutdownNow();
        }
    }
}
