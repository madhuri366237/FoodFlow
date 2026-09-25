package com.foodflow.dto.coupon;

import java.math.BigDecimal;

/** What the cart page shows after "Apply": the discount this coupon gives on THIS cart right now. */
public record CouponPreviewResponse(
        String code,
        String description,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total) {
}
