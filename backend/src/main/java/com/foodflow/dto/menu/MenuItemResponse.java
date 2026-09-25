package com.foodflow.dto.menu;

import com.foodflow.entity.MenuItem;

import java.math.BigDecimal;

public record MenuItemResponse(
        Long id,
        Long restaurantId,
        Long categoryId,
        String categoryName,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean available) {

    /**
     * Call only on items whose category was fetched with the query (@EntityGraph);
     * otherwise getCategory().getName() triggers one extra SELECT per item (N+1).
     * getRestaurant().getId() never triggers a query: the proxy already holds the id.
     */
    public static MenuItemResponse from(MenuItem item) {
        return new MenuItemResponse(item.getId(), item.getRestaurant().getId(), item.getCategory().getId(),
                item.getCategory().getName(), item.getName(), item.getDescription(), item.getPrice(),
                item.getImageUrl(), item.isAvailable());
    }
}
