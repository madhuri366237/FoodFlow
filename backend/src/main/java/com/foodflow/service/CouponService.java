package com.foodflow.service;

import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.coupon.AvailableCouponResponse;
import com.foodflow.dto.coupon.CouponPreviewResponse;
import com.foodflow.dto.coupon.CouponRequest;
import com.foodflow.dto.coupon.CouponResponse;
import com.foodflow.entity.Coupon;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService {

    List<AvailableCouponResponse> getAvailable();

    /** "Apply coupon" on the cart page: validates against the customer's server-priced cart. */
    CouponPreviewResponse previewForCart(Long customerId, String code);

    PageResponse<CouponResponse> list(Pageable pageable);

    CouponResponse create(CouponRequest request);

    CouponResponse update(Long id, CouponRequest request);

    // ---- Used by checkout, inside ITS transaction ----

    /** Validates the coupon for this subtotal and atomically takes one use. Throws InvalidCouponException. */
    Coupon redeem(String code, BigDecimal subtotal);

    /** Gives one use back (the order that used it was cancelled). */
    void release(Long couponId);
}
