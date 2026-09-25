package com.foodflow.service.impl;

import com.foodflow.dto.cart.CartResponse;
import com.foodflow.dto.coupon.CouponPreviewResponse;
import com.foodflow.dto.coupon.CouponRequest;
import com.foodflow.entity.Coupon;
import com.foodflow.entity.DiscountType;
import com.foodflow.exception.BadRequestException;
import com.foodflow.exception.ConflictException;
import com.foodflow.exception.InvalidCouponException;
import com.foodflow.repository.CouponRepository;
import com.foodflow.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.foodflow.TestFixtures.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Mock private CouponRepository couponRepository;
    @Mock private CartService cartService;

    private CouponServiceImpl couponService;
    private Coupon save10;

    @BeforeEach
    void setUp() {
        // A fixed clock makes "expired" deterministic, however slowly the test runs.
        couponService = new CouponServiceImpl(couponRepository, cartService, Clock.fixed(NOW, ZoneOffset.UTC));
        save10 = withId(new Coupon("SAVE10", DiscountType.PERCENTAGE, BigDecimal.TEN, NOW.plus(Duration.ofDays(1))), 7L);
    }

    @Test
    void validCouponIsRedeemedAtomically() {
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(save10));
        when(couponRepository.tryRedeem(7L, NOW)).thenReturn(1);

        assertThat(couponService.redeem("  save10 ", new BigDecimal("500"))).isSameAs(save10);
    }

    @Test
    void lostRaceForTheLastUseIsReportedAsLimitReached() {
        // Java-side checks passed, but another checkout took the last use first: the atomic UPDATE matched 0 rows.
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(save10));
        when(couponRepository.tryRedeem(7L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> couponService.redeem("SAVE10", new BigDecimal("500")))
                .isInstanceOf(InvalidCouponException.class)
                .extracting("errorCode").isEqualTo("COUPON_USAGE_LIMIT_REACHED");
    }

    @Test
    void unknownCodeIsInvalid() {
        when(couponRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.redeem("nope", BigDecimal.TEN))
                .extracting("errorCode").isEqualTo("INVALID_COUPON");
    }

    @Test
    void expiredCouponIsNeverRedeemed() {
        save10.setExpiresAt(NOW.minusSeconds(1));
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(save10));

        assertThatThrownBy(() -> couponService.redeem("SAVE10", new BigDecimal("500")))
                .extracting("errorCode").isEqualTo("COUPON_EXPIRED");
        verify(couponRepository, never()).tryRedeem(any(), any());
    }

    @Test
    void minimumOrderAmountIsChecked() {
        save10.setMinimumOrderAmount(new BigDecimal("600"));
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(save10));

        assertThatThrownBy(() -> couponService.redeem("SAVE10", new BigDecimal("500")))
                .extracting("errorCode").isEqualTo("COUPON_MINIMUM_NOT_MET");
    }

    @Test
    void previewUsesTheServerCartAndTakesNoUse() {
        when(cartService.getCart(1L)).thenReturn(new CartResponse(10L, "Biryani Blues",
                List.of(new com.foodflow.dto.cart.CartItemResponse(1L, 100L, "Biryani", new BigDecimal("250.00"), 2,
                        new BigDecimal("500.00"), true)), 2, new BigDecimal("500.00"), true));
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(save10));

        CouponPreviewResponse preview = couponService.previewForCart(1L, "SAVE10");

        assertThat(preview.discount()).isEqualByComparingTo("50.00");
        assertThat(preview.total()).isEqualByComparingTo("450.00");
        verify(couponRepository, never()).tryRedeem(any(), any());
    }

    @Test
    void previewOfEmptyCartIsRejected() {
        when(cartService.getCart(1L)).thenReturn(CartResponse.empty());

        assertThatThrownBy(() -> couponService.previewForCart(1L, "SAVE10")).isInstanceOf(BadRequestException.class);
    }

    // ---------- admin rules ----------

    private static CouponRequest adminRequest(String code, DiscountType type, String value, Instant expires) {
        return new CouponRequest(code, null, type, new BigDecimal(value), null, null, null, null, expires, null);
    }

    @Test
    void percentageAbove100IsRejected() {
        when(couponRepository.existsByCode("BIG")).thenReturn(false);

        assertThatThrownBy(() -> couponService.create(adminRequest("big", DiscountType.PERCENTAGE, "150", NOW.plus(Duration.ofDays(1)))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A percentage discount cannot exceed 100");
    }

    @Test
    void expiryMustBeInTheFuture() {
        when(couponRepository.existsByCode("PAST")).thenReturn(false);

        assertThatThrownBy(() -> couponService.create(adminRequest("PAST", DiscountType.FIXED_AMOUNT, "50", NOW.minusSeconds(60))))
                .hasMessage("expiresAt must be in the future");
    }

    @Test
    void duplicateCodeIsAConflict() {
        when(couponRepository.existsByCode("SAVE10")).thenReturn(true);

        assertThatThrownBy(() -> couponService.create(adminRequest("save10", DiscountType.PERCENTAGE, "10", NOW.plus(Duration.ofDays(1)))))
                .isInstanceOf(ConflictException.class);
        verify(couponRepository, never()).save(any());
    }
}
