package com.foodflow.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import com.foodflow.entity.CartItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * POST /api/cart/items body: {"menuItemId": 12, "quantity": 2}.
 *
 * <p>There is no price, name or restaurant field. The client says WHAT it wants and HOW MANY;
 * everything else comes from the database. A "price" property in the JSON is simply ignored.
 */
public record CartItemRequest(

        @NotNull(message = "menuItemId is required")
        @Positive(message = "menuItemId must be positive")
        @Schema(example = "1")
        Long menuItemId,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = CartItem.MAX_QUANTITY, message = "quantity must be at most " + CartItem.MAX_QUANTITY)
        @Schema(example = "2", minimum = "1", maximum = "20")
        Integer quantity) {
}
