package com.foodflow.service.impl;

import com.foodflow.dto.cart.CartResponse;
import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.coupon.AvailableCouponResponse;
import com.foodflow.dto.coupon.CouponPreviewResponse;
import com.foodflow.dto.coupon.CouponRequest;
import com.foodflow.dto.coupon.CouponResponse;
import com.foodflow.entity.Coupon;
import com.foodflow.entity.DiscountType;
import com.foodflow.exception.BadRequestException;
import com.foodflow.exception.ConflictException;
import com.foodflow.exception.InvalidCouponException;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.CouponRepository;
import com.foodflow.service.CartService;
import com.foodflow.service.CouponService;
import com.foodflow.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private static final Set<String> SORTABLE = Set.of("code", "expiresAt", "createdAt", "usedCount");
    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100");

    private final CouponRepository couponRepository;
    private final CartService cartService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<AvailableCouponResponse> getAvailable() {
        return couponRepository.findByActiveTrueAndExpiresAtAfterOrderByExpiresAtAsc(clock.instant()).stream()
                .filter(coupon -> coupon.getUsageLimit() == null || coupon.getUsedCount() < coupon.getUsageLimit())
                .map(AvailableCouponResponse::from)
                .toList();
    }

    /*
     * Read-only preview: validates and calculates but takes NO use. A use is only taken at
     * checkout. Otherwise customers who try a coupon and leave would burn through the limit.
     */
    @Override
    @Transactional(readOnly = true)
    public CouponPreviewResponse previewForCart(Long customerId, String code) {
        CartResponse cart = cartService.getCart(customerId);
        if (cart.items().isEmpty()) {
            throw new BadRequestException("Your cart is empty");
        }
        Coupon coupon = findUsable(code, cart.subtotal());
        BigDecimal discount = coupon.calculateDiscount(cart.subtotal());
        return new CouponPreviewResponse(coupon.getCode(), coupon.getDescription(), cart.subtotal(), discount,
                cart.subtotal().subtract(discount));
    }

    @Override
    @Transactional
    public Coupon redeem(String code, BigDecimal subtotal) {
        Coupon coupon = findUsable(code, subtotal);
        // The check above used a value read moments ago; another checkout may have taken the
        // last use since then. The atomic UPDATE is the real decision: 0 rows = no use left.
        if (couponRepository.tryRedeem(coupon.getId(), clock.instant()) == 0) {
            throw coupon.usageLimitReached();
        }
        return coupon;
    }

    @Override
    @Transactional
    public void release(Long couponId) {
        couponRepository.release(couponId);
    }

    private Coupon findUsable(String code, BigDecimal subtotal) {
        Coupon coupon = couponRepository.findByCode(Coupon.normalizeCode(code))
                .orElseThrow(() -> new InvalidCouponException("INVALID_COUPON", "Invalid coupon code"));
        coupon.checkUsable(subtotal, clock.instant());
        return coupon;
    }

    // ---------------- admin ----------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> list(Pageable pageable) {
        return PageResponse.from(couponRepository.findAll(PageableUtils.sanitize(pageable, SORTABLE))
                .map(CouponResponse::from));
    }

    @Override
    @Transactional
    public CouponResponse create(CouponRequest request) {
        String code = Coupon.normalizeCode(request.code());
        if (couponRepository.existsByCode(code)) {
            throw new ConflictException("Coupon " + code + " already exists");
        }
        if (!request.expiresAt().isAfter(clock.instant())) {
            throw new BadRequestException("expiresAt must be in the future");
        }
        Coupon coupon = new Coupon(code, request.discountType(), request.discountValue(), request.expiresAt());
        applyEditableFields(coupon, request);
        return CouponResponse.from(couponRepository.save(coupon));
    }

    @Override
    @Transactional
    public CouponResponse update(Long id, CouponRequest request) {
        Coupon coupon = couponRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Coupon", id));
        if (!coupon.getCode().equals(Coupon.normalizeCode(request.code()))) {
            throw new BadRequestException("A coupon's code cannot be changed; create a new coupon instead");
        }
        if (request.usageLimit() != null && request.usageLimit() < coupon.getUsedCount()) {
            throw new BadRequestException("usageLimit cannot be lower than the " + coupon.getUsedCount()
                    + " uses already made");
        }
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue());
        coupon.setExpiresAt(request.expiresAt());
        applyEditableFields(coupon, request);
        return CouponResponse.from(couponRepository.saveAndFlush(coupon));
    }

    /** Shared by create and update, with the rules that span several fields. */
    private static void applyEditableFields(Coupon coupon, CouponRequest request) {
        if (request.discountType() == DiscountType.PERCENTAGE && request.discountValue().compareTo(MAX_PERCENTAGE) > 0) {
            throw new BadRequestException("A percentage discount cannot exceed 100");
        }
        if (request.startsAt() != null && !request.startsAt().isBefore(request.expiresAt())) {
            throw new BadRequestException("startsAt must be before expiresAt");
        }
        coupon.setDescription(request.description());
        coupon.setMinimumOrderAmount(request.minimumOrderAmount() == null ? BigDecimal.ZERO : request.minimumOrderAmount());
        coupon.setMaximumDiscount(request.maximumDiscount());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setStartsAt(request.startsAt());
        coupon.setActive(request.active() == null || request.active());
    }
}
