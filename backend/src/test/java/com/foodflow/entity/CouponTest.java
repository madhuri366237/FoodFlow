package com.foodflow.entity;

import com.foodflow.exception.InvalidCouponException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Discount arithmetic and eligibility rules: pure Java, no Spring, no database. */
class CouponTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    private static Coupon percentage(String value, String cap) {
        Coupon coupon = new Coupon("save", DiscountType.PERCENTAGE, new BigDecimal(value), NOW.plus(Duration.ofDays(30)));
        coupon.setMaximumDiscount(cap == null ? null : new BigDecimal(cap));
        return coupon;
    }

    private static Coupon fixed(String value) {
        return new Coupon("flat", DiscountType.FIXED_AMOUNT, new BigDecimal(value), NOW.plus(Duration.ofDays(30)));
    }

    // ---------------- discount calculation ----------------

    @ParameterizedTest(name = "{0}% of {1}, cap {2} -> {3}")
    @CsvSource(nullValues = "none", value = {
            "10, 1000.00, none,   100.00",   // the spec's example: SAVE10 on 1000 = 100
            "10, 410.00,  none,    41.00",   // the cart example: 410 - 41 = 369
            "50, 1000.00, 200.00, 200.00",   // cap applies: 500 would exceed it
            "50, 300.00,  200.00, 150.00",   // cap not reached
            "10, 333.33,  none,    33.33",   // 33.333 rounds half-up to paise
            "12.5, 99.99, none,    12.50",   // 12.49875 -> 12.50
            "100, 250.00, none,   250.00"    // free order, total 0
    })
    void percentageDiscount(String percent, String subtotal, String cap, String expected) {
        assertThat(percentage(percent, cap).calculateDiscount(new BigDecimal(subtotal)))
                .isEqualByComparingTo(expected);
    }

    @Test
    void fixedDiscountIsTheValue() {
        assertThat(fixed("150").calculateDiscount(new BigDecimal("1000.00"))).isEqualByComparingTo("150.00");
    }

    @Test
    void discountNeverExceedsTheSubtotal() {
        // Flat 500 off a 300 order: discount 300, total 0, never -200.
        assertThat(fixed("500").calculateDiscount(new BigDecimal("300.00"))).isEqualByComparingTo("300.00");
    }

    @Test
    void codesAreNormalised() {
        assertThat(new Coupon("  save10 ", DiscountType.FIXED_AMOUNT, BigDecimal.TEN, NOW).getCode()).isEqualTo("SAVE10");
    }

    // ---------------- eligibility ----------------

    @Test
    void usableCouponPasses() {
        percentage("10", null).checkUsable(new BigDecimal("100"), NOW);
    }

    @Test
    void expiredCoupon() {
        Coupon coupon = percentage("10", null);
        coupon.setExpiresAt(NOW); // expiry instant itself counts as expired

        assertThatThrownBy(() -> coupon.checkUsable(new BigDecimal("100"), NOW))
                .isInstanceOf(InvalidCouponException.class)
                .extracting("errorCode").isEqualTo("COUPON_EXPIRED");
    }

    @Test
    void notStartedYet() {
        Coupon coupon = percentage("10", null);
        coupon.setStartsAt(NOW.plus(Duration.ofDays(1)));

        assertThatThrownBy(() -> coupon.checkUsable(new BigDecimal("100"), NOW))
                .extracting("errorCode").isEqualTo("COUPON_NOT_STARTED");
    }

    @Test
    void inactiveLooksExactlyLikeAnUnknownCode() {
        Coupon coupon = percentage("10", null);
        coupon.setActive(false);

        assertThatThrownBy(() -> coupon.checkUsable(new BigDecimal("100"), NOW))
                .hasMessage("Invalid coupon code")
                .extracting("errorCode").isEqualTo("INVALID_COUPON");
    }

    @Test
    void minimumOrderAmountBoundary() {
        Coupon coupon = percentage("10", null);
        coupon.setMinimumOrderAmount(new BigDecimal("500"));

        coupon.checkUsable(new BigDecimal("500.00"), NOW); // exactly the minimum is fine
        assertThatThrownBy(() -> coupon.checkUsable(new BigDecimal("499.99"), NOW))
                .hasMessageContaining("Add 0.01 more")
                .extracting("errorCode").isEqualTo("COUPON_MINIMUM_NOT_MET");
    }

    @Test
    void usageLimitReached() {
        Coupon coupon = percentage("10", null);
        coupon.setUsageLimit(3);
        ReflectionTestUtils.setField(coupon, "usedCount", 3);

        assertThatThrownBy(() -> coupon.checkUsable(new BigDecimal("100"), NOW))
                .extracting("errorCode").isEqualTo("COUPON_USAGE_LIMIT_REACHED");
    }
}
