package com.foodflow.dto.cart;

import java.math.BigDecimal;

/**
 * One cart line with its CURRENT price.
 *
 * @param available false if the owner marked the dish unavailable after it was added.
 *                  Such lines are shown but excluded from the subtotal, and they block checkout.
 */
public record CartItemResponse(
        Long id,
        Long menuItemId,
        String name,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        boolean available) {
}
