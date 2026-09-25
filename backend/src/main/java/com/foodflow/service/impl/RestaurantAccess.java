package com.foodflow.service.impl;

import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.exception.ForbiddenException;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.MenuItemRepository;
import com.foodflow.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The ownership rule, in exactly one place: "a restaurant owner may only change their OWN
 * restaurant and its menu". Every write in RestaurantServiceImpl and MenuItemServiceImpl
 * goes through here, so the rule can't be forgotten in one method and remembered in another.
 *
 * <p>Why 403 and not 404 for someone else's restaurant? Restaurants and menus are public;
 * anyone can already see that restaurant 7 exists, so hiding it with a 404 gains nothing,
 * and 403 tells the client exactly what went wrong. Private data (other people's orders
 * and addresses, from Phase 5 on) uses 404 instead, so its existence isn't revealed.
 */
@Component
@RequiredArgsConstructor
class RestaurantAccess {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;

    Restaurant requireOwnedRestaurant(Long restaurantId, Long ownerId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> ResourceNotFoundException.of("Restaurant", restaurantId));
        // getOwner().getId() reads the FK from the lazy proxy: no query for the owner.
        if (!restaurant.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("You can only manage your own restaurants");
        }
        return restaurant;
    }

    MenuItem requireOwnedMenuItem(Long menuItemId, Long ownerId) {
        MenuItem item = menuItemRepository.findWithRestaurantById(menuItemId)
                .orElseThrow(() -> ResourceNotFoundException.of("Menu item", menuItemId));
        if (!item.getRestaurant().getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("You can only manage menu items of your own restaurants");
        }
        return item;
    }
}
