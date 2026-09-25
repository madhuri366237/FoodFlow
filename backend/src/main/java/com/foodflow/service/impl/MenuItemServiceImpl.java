package com.foodflow.service.impl;

import com.foodflow.dto.menu.MenuItemRequest;
import com.foodflow.dto.menu.MenuItemResponse;
import com.foodflow.entity.Category;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.exception.BadRequestException;
import com.foodflow.exception.ConflictException;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.CategoryRepository;
import com.foodflow.repository.MenuItemRepository;
import com.foodflow.repository.RestaurantRepository;
import com.foodflow.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuItemServiceImpl implements MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final RestaurantAccess restaurantAccess;

    /*
     * 2 queries in total, however many dishes there are:
     *   1. the restaurant (must exist and be active)
     *   2. its available dishes JOIN categories (@EntityGraph)
     */
    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getPublicMenu(Long restaurantId) {
        if (restaurantRepository.findByIdAndActiveTrue(restaurantId).isEmpty()) {
            throw ResourceNotFoundException.of("Restaurant", restaurantId);
        }
        return menuItemRepository.findByRestaurantIdAndAvailableTrueOrderByNameAsc(restaurantId).stream()
                .map(MenuItemResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getOwnerMenu(Long ownerId, Long restaurantId) {
        restaurantAccess.requireOwnedRestaurant(restaurantId, ownerId);
        return menuItemRepository.findByRestaurantIdOrderByNameAsc(restaurantId).stream()
                .map(MenuItemResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public MenuItemResponse create(Long ownerId, Long restaurantId, MenuItemRequest request) {
        Restaurant restaurant = restaurantAccess.requireOwnedRestaurant(restaurantId, ownerId);
        String name = request.name().trim();
        if (menuItemRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name)) {
            throw new ConflictException("This restaurant already has a dish named '" + name + "'");
        }

        MenuItem item = new MenuItem(restaurant, findCategory(request.categoryId()), name,
                request.description(), request.price(), request.imageUrl());
        if (request.available() != null) {
            item.setAvailable(request.available());
        }
        return MenuItemResponse.from(menuItemRepository.save(item));
    }

    @Override
    @Transactional
    public MenuItemResponse update(Long ownerId, Long menuItemId, MenuItemRequest request) {
        MenuItem item = restaurantAccess.requireOwnedMenuItem(menuItemId, ownerId);
        String name = request.name().trim();
        if (menuItemRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(
                item.getRestaurant().getId(), name, menuItemId)) {
            throw new ConflictException("This restaurant already has a dish named '" + name + "'");
        }

        // The price change affects only FUTURE orders: from Phase 6, each order line
        // stores its own copy of the price at the moment of ordering.
        item.setCategory(findCategory(request.categoryId()));
        item.setName(name);
        item.setDescription(request.description());
        item.setPrice(request.price());
        item.setImageUrl(request.imageUrl());
        if (request.available() != null) {
            item.setAvailable(request.available());
        }
        return MenuItemResponse.from(menuItemRepository.saveAndFlush(item));
    }

    @Override
    @Transactional
    public MenuItemResponse setAvailability(Long ownerId, Long menuItemId, boolean available) {
        MenuItem item = restaurantAccess.requireOwnedMenuItem(menuItemId, ownerId);
        item.setAvailable(available);
        return MenuItemResponse.from(menuItemRepository.saveAndFlush(item));
    }

    @Override
    @Transactional
    public void delete(Long ownerId, Long menuItemId) {
        MenuItem item = restaurantAccess.requireOwnedMenuItem(menuItemId, ownerId);
        menuItemRepository.delete(item);
    }

    /** A categoryId that doesn't exist is a mistake in the request body: 400, not 404. */
    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BadRequestException("Category " + categoryId + " does not exist"));
    }
}
