package com.foodflow.controller;

import org.springdoc.core.annotations.ParameterObject;
import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.coupon.CouponRequest;
import com.foodflow.dto.coupon.CouponResponse;
import com.foodflow.service.CouponService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Coupon management; ADMIN only via the /api/admin/** URL rule. There is no DELETE: a coupon
 * that orders refer to must stay; set "active": false to retire it.
 */
@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;

    @Operation(summary = "List coupons (admin)")
    @GetMapping
    public PageResponse<CouponResponse> list(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return couponService.list(pageable);
    }

    @Operation(summary = "Create a coupon (admin)")
    @PostMapping
    public ResponseEntity<CouponResponse> create(@Valid @RequestBody CouponRequest request) {
        CouponResponse created = couponService.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri()).body(created);
    }

    @Operation(summary = "Update a coupon; the code can't change (admin)")
    @PutMapping("/{id}")
    public CouponResponse update(@PathVariable Long id, @Valid @RequestBody CouponRequest request) {
        return couponService.update(id, request);
    }
}
