package com.foodflow.dto.cart;

import com.foodflow.util.MoneyUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * The cart as the customer sees it; every amount is computed on the server from current prices.
 *
 * @param checkoutReady true only if the cart has items, every item is still available,
 *                      and the restaurant is active and open. Phase 6 re-checks all of this.
 */
public record CartResponse(
        Long restaurantId,
        String restaurantName,
        List<CartItemResponse> items,
        int totalQuantity,
        BigDecimal subtotal,
        boolean checkoutReady) {

    public static CartResponse empty() {
        return new CartResponse(null, null, List.of(), 0, MoneyUtils.ZERO, false);
    }
}
