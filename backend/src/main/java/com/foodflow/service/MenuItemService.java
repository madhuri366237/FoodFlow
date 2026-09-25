package com.foodflow.service;

import com.foodflow.dto.menu.MenuItemRequest;
import com.foodflow.dto.menu.MenuItemResponse;

import java.util.List;

public interface MenuItemService {

    /** Customer view: available dishes of an active restaurant. */
    List<MenuItemResponse> getPublicMenu(Long restaurantId);

    /** Owner view: every dish, including unavailable ones. */
    List<MenuItemResponse> getOwnerMenu(Long ownerId, Long restaurantId);

    MenuItemResponse create(Long ownerId, Long restaurantId, MenuItemRequest request);

    MenuItemResponse update(Long ownerId, Long menuItemId, MenuItemRequest request);

    MenuItemResponse setAvailability(Long ownerId, Long menuItemId, boolean available);

    void delete(Long ownerId, Long menuItemId);
}
