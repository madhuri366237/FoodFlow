package com.foodflow.dto.coupon;

import com.foodflow.entity.DiscountType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Admin create/update body. Rules spanning two fields (percentage at most 100, start before
 * expiry, limit not below uses already made) are checked in the service.
 * On update the code can't change (it's printed on flyers, sent in SMS...).
 */
public record CouponRequest(

        @NotBlank(message = "code is required")
        @Pattern(regexp = "^[A-Za-z0-9]{3,30}$", message = "code must be 3-30 letters or digits")
        String code,

        @Size(max = 255, message = "description must be at most 255 characters")
        String description,

        @NotNull(message = "discountType is required (PERCENTAGE or FIXED_AMOUNT)")
        DiscountType discountType,

        @NotNull(message = "discountValue is required")
        @Positive(message = "discountValue must be greater than 0")
        @Digits(integer = 8, fraction = 2, message = "discountValue must have at most 2 decimal places")
        BigDecimal discountValue,

        @PositiveOrZero(message = "minimumOrderAmount must not be negative")
        @Digits(integer = 8, fraction = 2, message = "minimumOrderAmount must have at most 2 decimal places")
        BigDecimal minimumOrderAmount,

        @Positive(message = "maximumDiscount must be greater than 0")
        @Digits(integer = 8, fraction = 2, message = "maximumDiscount must have at most 2 decimal places")
        BigDecimal maximumDiscount,

        @Positive(message = "usageLimit must be greater than 0")
        Integer usageLimit,

        Instant startsAt,

        @NotNull(message = "expiresAt is required")
        Instant expiresAt,

        Boolean active) {
}
