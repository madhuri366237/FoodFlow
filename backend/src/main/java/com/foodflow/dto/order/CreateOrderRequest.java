package com.foodflow.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import com.foodflow.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * POST /api/orders body: {"addressId": 3, "paymentMethod": "UPI", "couponCode": "SAVE10"}.
 *
 * <p>No items, no prices, no discount amount: the order is built entirely on the server from
 * the customer's cart, the current menu prices and the coupon's rules. The client chooses only
 * WHERE to deliver, HOW to pay and WHICH coupon to try.
 */
public record CreateOrderRequest(
        @NotNull(message = "addressId is required")
        @Positive(message = "addressId must be positive")
        @Schema(example = "1")
        Long addressId,

        @NotNull(message = "paymentMethod is required (CARD, UPI or CASH_ON_DELIVERY)")
        @Schema(example = "UPI")
        PaymentMethod paymentMethod,

        // Optional.
        @Size(max = 30, message = "couponCode must be at most 30 characters")
        @Schema(example = "SAVE10", nullable = true)
        String couponCode) {

    public boolean hasCoupon() {
        return couponCode != null && !couponCode.isBlank();
    }
}
