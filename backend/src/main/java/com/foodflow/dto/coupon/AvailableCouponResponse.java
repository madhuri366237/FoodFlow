package com.foodflow.dto.coupon;

import com.foodflow.entity.Coupon;
import com.foodflow.entity.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

/** Customer view ("Offers"): no usage counts or internal flags. */
public record AvailableCouponResponse(
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscount,
        Instant expiresAt) {

    public static AvailableCouponResponse from(Coupon coupon) {
        return new AvailableCouponResponse(coupon.getCode(), coupon.getDescription(), coupon.getDiscountType(),
                coupon.getDiscountValue(), coupon.getMinimumOrderAmount(), coupon.getMaximumDiscount(),
                coupon.getExpiresAt());
    }
}
