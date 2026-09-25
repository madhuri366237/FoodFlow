package com.foodflow.dto.coupon;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/coupons/validate body: {"code":"SAVE10"}.
 * No order amount: the coupon is checked against the customer's CART, priced on the server.
 * Otherwise a client could claim a 10,000 subtotal to unlock a coupon for a 100 order.
 */
public record ValidateCouponRequest(
        @NotBlank(message = "code is required")
        @Size(max = 30, message = "code must be at most 30 characters")
        @Schema(example = "SAVE10")
        String code) {
}
