package com.foodflow.dto.cart;

import com.foodflow.entity.CartItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /api/cart/items/{id} body: the NEW quantity (not a delta).
 * Setting an absolute value is idempotent: sending the same request twice gives the same
 * result, which a "+1" request would not. Use DELETE to remove a line, not quantity 0.
 */
public record UpdateCartItemRequest(

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1 (use DELETE to remove the item)")
        @Max(value = CartItem.MAX_QUANTITY, message = "quantity must be at most " + CartItem.MAX_QUANTITY)
        Integer quantity) {
}
