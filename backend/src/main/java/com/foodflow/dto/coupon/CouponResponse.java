package com.foodflow.dto.coupon;

import com.foodflow.entity.Coupon;
import com.foodflow.entity.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

/** Admin view: includes usage figures and the active flag. */
public record CouponResponse(
        Long id,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscount,
        Integer usageLimit,
        int usedCount,
        Instant startsAt,
        Instant expiresAt,
        boolean active) {

    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(coupon.getId(), coupon.getCode(), coupon.getDescription(),
                coupon.getDiscountType(), coupon.getDiscountValue(), coupon.getMinimumOrderAmount(),
                coupon.getMaximumDiscount(), coupon.getUsageLimit(), coupon.getUsedCount(), coupon.getStartsAt(),
                coupon.getExpiresAt(), coupon.isActive());
    }
}
