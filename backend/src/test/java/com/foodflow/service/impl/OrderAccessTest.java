package com.foodflow.service.impl;

import com.foodflow.entity.Order;
import com.foodflow.entity.PaymentMethod;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.OrderRepository;
import com.foodflow.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.foodflow.TestFixtures.restaurant;
import static com.foodflow.TestFixtures.user;
import static com.foodflow.TestFixtures.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Who may see an order: its customer, the owner of its restaurant, or an admin. Nobody else. */
@ExtendWith(MockitoExtension.class)
class OrderAccessTest {

    @Mock private OrderRepository orderRepository;
    @InjectMocks private OrderAccess orderAccess;

    private User customer;
    private User owner;
    private Order order;

    @BeforeEach
    void setUp() {
        customer = user(1L, Role.CUSTOMER);
        owner = user(2L, Role.RESTAURANT_OWNER);
        Restaurant restaurant = restaurant(10L, owner, "Biryani Blues");
        order = withId(new Order(customer, restaurant, null, "Home", PaymentMethod.CARD), 900L);
        when(orderRepository.findDetailedById(900L)).thenReturn(Optional.of(order));
    }

    @Test
    void customerSeesOwnOrder() {
        assertThat(orderAccess.requireVisible(900L, UserPrincipal.from(customer))).isSameAs(order);
    }

    @Test
    void ownerSeesOrdersOfOwnRestaurant() {
        assertThat(orderAccess.requireVisible(900L, UserPrincipal.from(owner))).isSameAs(order);
    }

    @Test
    void adminSeesEverything() {
        assertThat(orderAccess.requireVisible(900L, UserPrincipal.from(user(3L, Role.ADMIN)))).isSameAs(order);
    }

    @Test
    void otherCustomerGets404NotForbidden() {
        // 404 rather than 403, so an outsider can't even tell that order 900 exists.
        assertThatThrownBy(() -> orderAccess.requireVisible(900L, UserPrincipal.from(user(4L, Role.CUSTOMER))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void otherOwnerGets404() {
        assertThatThrownBy(() -> orderAccess.requireVisible(900L, UserPrincipal.from(user(5L, Role.RESTAURANT_OWNER))))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
