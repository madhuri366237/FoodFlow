package com.foodflow.service.impl;

import com.foodflow.TestcontainersConfiguration;
import com.foodflow.dto.cart.CartItemRequest;
import com.foodflow.dto.order.CreateOrderRequest;
import com.foodflow.entity.Address;
import com.foodflow.entity.Category;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.PaymentMethod;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.repository.AddressRepository;
import com.foodflow.repository.CategoryRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The "A" in ACID, demonstrated against real PostgreSQL.
 *
 * <p>Checkout inserts the order and its lines FIRST, then empties the cart. A temporary trigger
 * makes that last step fail, like a crash halfway through checkout. Because everything runs in
 * one transaction, the order rows that WERE already inserted must disappear too, and the cart
 * must be untouched.
 *
 * <p>Not {@code @Transactional}: the service must run and roll back its own real transaction.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderAtomicityIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private CartService cartService;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private JdbcTemplate jdbc;

    private User customer;
    private User owner;
    private Address address;
    private MenuItem dish;

    @BeforeEach
    void setUp() {
        customer = userRepository.save(new User("C", "atomic-customer@example.com", "h", null, Role.CUSTOMER));
        owner = userRepository.save(new User("O", "atomic-owner@example.com", "h", null, Role.RESTAURANT_OWNER));
        Category category = categoryRepository.findByNameIgnoreCase("Biryani").orElseThrow();
        Restaurant restaurant = restaurantRepository.save(new Restaurant(owner, "Atomic Cafe", null, "1 Road", "9000000001", null));
        dish = menuItemRepository.save(new MenuItem(restaurant, category, "Atomic Biryani", null, new BigDecimal("250.00"), null));
        address = addressRepository.save(new Address(customer, "Home", "1 Road", null, "Bengaluru", "Karnataka", "560001", true));
        cartService.addItem(customer.getId(), new CartItemRequest(dish.getId(), 2));
    }

    @AfterEach
    void cleanUp() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_cart_clear ON cart_items");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_cart_clear()");
        // Payments first: payments -> orders is RESTRICT (payment history is never cascaded away).
        jdbc.update("delete from payments where order_id in (select id from orders where user_id = ?)", customer.getId());
        jdbc.update("delete from orders where user_id = ?", customer.getId());
        jdbc.update("delete from restaurants where owner_id = ?", owner.getId());
        jdbc.update("delete from users where id in (?, ?)", customer.getId(), owner.getId());
    }

    @Test
    void failureAfterOrderInsertRollsBackTheWholeCheckout() {
        jdbc.execute("""
                CREATE FUNCTION fail_cart_clear() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'simulated crash while emptying the cart'; END;
                $$ LANGUAGE plpgsql""");
        jdbc.execute("CREATE TRIGGER fail_cart_clear BEFORE DELETE ON cart_items "
                + "FOR EACH ROW EXECUTE FUNCTION fail_cart_clear()");

        assertThatThrownBy(() -> orderService.placeOrder(customer.getId(), new CreateOrderRequest(address.getId(), PaymentMethod.CASH_ON_DELIVERY, null)))
                .hasStackTraceContaining("simulated crash while emptying the cart");

        // The INSERTs into orders/order_items did run, but were rolled back with everything else.
        assertThat(jdbc.queryForObject("select count(*) from orders where user_id = ?", Long.class, customer.getId()))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from order_items", Long.class)).isZero();
        // The cart is exactly as it was: nothing lost, and the customer can simply retry.
        assertThat(cartService.getCart(customer.getId()).items()).hasSize(1);
        assertThat(cartService.getCart(customer.getId()).totalQuantity()).isEqualTo(2);
    }

    @Test
    void withoutTheFailureTheSameCheckoutCommits() {
        orderService.placeOrder(customer.getId(), new CreateOrderRequest(address.getId(), PaymentMethod.CASH_ON_DELIVERY, null));

        assertThat(jdbc.queryForObject("select count(*) from orders where user_id = ?", Long.class, customer.getId()))
                .isEqualTo(1);
        assertThat(cartService.getCart(customer.getId()).items()).isEmpty();
    }
}
