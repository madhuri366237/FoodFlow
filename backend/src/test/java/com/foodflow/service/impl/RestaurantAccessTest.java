package com.foodflow.service.impl;

import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.exception.ForbiddenException;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.MenuItemRepository;
import com.foodflow.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** The ownership rule in isolation: no Spring, no database. */
@ExtendWith(MockitoExtension.class)
class RestaurantAccessTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_OWNER_ID = 2L;

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private MenuItemRepository menuItemRepository;

    @InjectMocks
    private RestaurantAccess restaurantAccess;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        User owner = new User("Owner", "owner@example.com", "hash", null, Role.RESTAURANT_OWNER);
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
        restaurant = new Restaurant(owner, "Spice Hub", null, "1 Road", "9876543210", null);
        ReflectionTestUtils.setField(restaurant, "id", 10L);
    }

    @Test
    void ownerGetsOwnRestaurant() {
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));

        assertThat(restaurantAccess.requireOwnedRestaurant(10L, OWNER_ID)).isSameAs(restaurant);
    }

    @Test
    void otherOwnerIsForbidden() {
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> restaurantAccess.requireOwnedRestaurant(10L, OTHER_OWNER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void missingRestaurantIsNotFound() {
        when(restaurantRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantAccess.requireOwnedRestaurant(10L, OWNER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void menuItemOwnershipFollowsItsRestaurant() {
        MenuItem item = new MenuItem(restaurant, null, "Biryani", null, new BigDecimal("250"), null);
        when(menuItemRepository.findWithRestaurantById(5L)).thenReturn(Optional.of(item));

        assertThat(restaurantAccess.requireOwnedMenuItem(5L, OWNER_ID)).isSameAs(item);
        assertThatThrownBy(() -> restaurantAccess.requireOwnedMenuItem(5L, OTHER_OWNER_ID))
                .isInstanceOf(ForbiddenException.class);
    }
}
