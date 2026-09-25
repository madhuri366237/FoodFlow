package com.foodflow.entity;

import com.foodflow.exception.InvalidCouponException;
import com.foodflow.util.MoneyUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;

/**
 * A discount coupon. The rules ("can this coupon be used on this subtotal right now?") and the
 * arithmetic ("how much does it take off?") live HERE, as plain Java with no database access,
 * so they are identical for the cart preview and for checkout and can be unit-tested directly.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "coupons")
public class Coupon extends BaseEntity {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Setter(AccessLevel.NONE) // a code never changes once issued
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "minimum_order_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumOrderAmount = BigDecimal.ZERO;

    @Column(name = "maximum_discount", precision = 10, scale = 2)
    private BigDecimal maximumDiscount;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    // Only changed in the database by the atomic tryRedeem/release queries.
    @Setter(AccessLevel.NONE)
    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active = true;

    public Coupon(String code, DiscountType discountType, BigDecimal discountValue, Instant expiresAt) {
        this.code = normalizeCode(code);
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.expiresAt = expiresAt;
    }

    /** "  save10 " and "SAVE10" are the same coupon. */
    public static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Throws InvalidCouponException with a precise reason if the coupon can't be used for this
     * subtotal at this moment. The checks are ordered from "doesn't exist for you" to
     * "exists but not for this cart".
     */
    public void checkUsable(BigDecimal subtotal, Instant now) {
        if (!active) {
            // Same message as an unknown code: don't reveal that a disabled coupon exists.
            throw new InvalidCouponException("INVALID_COUPON", "Invalid coupon code");
        }
        if (startsAt != null && now.isBefore(startsAt)) {
            throw new InvalidCouponException("COUPON_NOT_STARTED", "Coupon " + code + " is not active yet");
        }
        if (!now.isBefore(expiresAt)) {
            throw new InvalidCouponException("COUPON_EXPIRED", "Coupon " + code + " has expired");
        }
        if (usageLimit != null && usedCount >= usageLimit) {
            throw usageLimitReached();
        }
        if (subtotal.compareTo(minimumOrderAmount) < 0) {
            throw new InvalidCouponException("COUPON_MINIMUM_NOT_MET",
                    "Coupon %s needs a minimum order of %s (your order: %s). Add %s more."
                            .formatted(code, MoneyUtils.normalize(minimumOrderAmount), MoneyUtils.normalize(subtotal),
                                    MoneyUtils.normalize(minimumOrderAmount.subtract(subtotal))));
        }
    }

    public InvalidCouponException usageLimitReached() {
        return new InvalidCouponException("COUPON_USAGE_LIMIT_REACHED", "Coupon " + code + " has been fully redeemed");
    }

    /**
     * The discount for a subtotal (eligibility is checked separately by checkUsable):
     * <pre>
     *   1. raw     = PERCENTAGE:   subtotal x value / 100, rounded half-up to paise
     *                FIXED_AMOUNT: value
     *   2. capped  = min(raw, maximumDiscount)   if a cap is set
     *   3. final   = min(capped, subtotal)       a discount can never make the total negative
     * </pre>
     * Examples: 10% of 1000 = 100. 50% of 1000 capped at 200 = 200. Flat 150 on a 100 order = 100.
     */
    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        BigDecimal raw = switch (discountType) {
            case PERCENTAGE -> subtotal.multiply(discountValue).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> discountValue;
        };
        BigDecimal capped = maximumDiscount != null ? raw.min(maximumDiscount) : raw;
        return MoneyUtils.normalize(capped.min(subtotal));
    }
}
