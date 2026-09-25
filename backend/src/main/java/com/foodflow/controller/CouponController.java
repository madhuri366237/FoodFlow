package com.foodflow.controller;

import com.foodflow.dto.coupon.AvailableCouponResponse;
import com.foodflow.dto.coupon.CouponPreviewResponse;
import com.foodflow.dto.coupon.ValidateCouponRequest;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.CouponService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Coupons")
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /** "Offers" list for a logged-in user. */
    @Operation(summary = "Currently available coupons")
    @GetMapping
    public List<AvailableCouponResponse> available() {
        return couponService.getAvailable();
    }

    /**
     * 200 with the discount if the coupon works on the caller's cart; otherwise
     * 400 with a precise code (INVALID_COUPON, COUPON_EXPIRED, COUPON_MINIMUM_NOT_MET, ...).
     */
    @Operation(summary = "Preview a coupon on your current cart (does not use it up)")
    @PostMapping("/validate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CouponPreviewResponse validate(@AuthenticationPrincipal UserPrincipal me,
                                         @Valid @RequestBody ValidateCouponRequest request) {
        return couponService.previewForCart(me.getId(), request.code());
    }
}
