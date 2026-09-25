package com.foodflow.service.impl;

import com.foodflow.TestcontainersConfiguration;
import com.foodflow.dto.cart.CartItemRequest;
import com.foodflow.dto.order.CreateOrderRequest;
import com.foodflow.dto.payment.PaymentRequest;
import com.foodflow.dto.payment.PaymentResponse;
import com.foodflow.entity.Address;
import com.foodflow.entity.Category;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.OrderPaymentStatus;
import com.foodflow.entity.OrderStatus;
import com.foodflow.entity.PaymentMethod;
import com.foodflow.entity.PaymentStatus;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.repository.AddressRepository;
import com.foodflow.repository.CategoryRepository;
import com.foodflow.repository.MenuItemRepository;
import com.foodflow.repository.RestaurantRepository;
import com.foodflow.repository.UserRepository;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.CartService;
import com.foodflow.service.OrderService;
import com.foodflow.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refunds run in an AFTER_COMMIT event listener. A {@code @Transactional} test never commits
 * (it rolls back at the end), so it could never observe a refund. This test therefore runs the
 * services with real commits and cleans up afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PaymentRefundIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private CartService cartService;
    @Autowired private PaymentService paymentService;
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
        customer = userRepository.save(new User("C", "refund-customer@example.com", "h", null, Role.CUSTOMER));
        owner = userRepository.save(new User("O", "refund-owner@example.com", "h", null, Role.RESTAURANT_OWNER));
        Category category = categoryRepository.findByNameIgnoreCase("Biryani").orElseThrow();
        Restaurant restaurant = restaurantRepository.save(new Restaurant(owner, "Refund Cafe", null, "1 Road", "9000000001", null));
        dish = menuItemRepository.save(new MenuItem(restaurant, category, "Refund Biryani", null, new BigDecimal("250.00"), null));
        address = addressRepository.save(new Address(customer, "Home", "1 Road", null, "Bengaluru", "Karnataka", "560001", true));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from payments where order_id in (select id from orders where user_id = ?)", customer.getId());
        jdbc.update("delete from orders where user_id = ?", customer.getId());
        jdbc.update("delete from restaurants where owner_id = ?", owner.getId());
        jdbc.update("delete from users where id in (?, ?)", customer.getId(), owner.getId());
    }

    private Long paidCardOrder() {
        cartService.addItem(customer.getId(), new CartItemRequest(dish.getId(), 2));
        Long orderId = orderService.placeOrder(customer.getId(),
                new CreateOrderRequest(address.getId(), PaymentMethod.CARD, null)).id();
        PaymentResponse payment = paymentService.pay(customer.getId(), new PaymentRequest(orderId, "tok_visa"));
        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        return orderId;
    }

    @Test
    void cancellingAPaidOrderRefundsItAfterCommit() {
        Long orderId = paidCardOrder();

        orderService.cancelByCustomer(orderId, customer.getId(), "Changed my mind");

        UserPrincipal me = UserPrincipal.from(customer);
        List<PaymentResponse> payments = paymentService.getPaymentsForOrder(orderId, me);
        assertThat(payments).hasSize(1);
        assertThat(payments.getFirst().status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payments.getFirst().refundReference()).startsWith("SIM_RFND_");
        assertThat(orderService.getOrder(orderId, me).paymentStatus()).isEqualTo(OrderPaymentStatus.REFUNDED);
        assertThat(orderService.getOrder(orderId, me).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancellingAnUnpaidOnlineOrderRefundsNothing() {
        cartService.addItem(customer.getId(), new CartItemRequest(dish.getId(), 1));
        Long orderId = orderService.placeOrder(customer.getId(),
                new CreateOrderRequest(address.getId(), PaymentMethod.UPI, null)).id();

        orderService.cancelByCustomer(orderId, customer.getId(), null);

        UserPrincipal me = UserPrincipal.from(customer);
        assertThat(paymentService.getPaymentsForOrder(orderId, me)).isEmpty();
        assertThat(orderService.getOrder(orderId, me).paymentStatus()).isEqualTo(OrderPaymentStatus.PENDING);
    }
}
