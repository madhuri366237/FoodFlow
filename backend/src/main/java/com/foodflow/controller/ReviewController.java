package com.foodflow.controller;

import org.springdoc.core.annotations.ParameterObject;
import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.review.ReviewRequest;
import com.foodflow.dto.review.ReviewResponse;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.ReviewService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reviews")
@RestController
@RequestMapping("/api/restaurants/{restaurantId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** Only customers; the service also requires a delivered order of their own. */
    @Operation(summary = "Review a restaurant for one of your delivered orders")
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReviewResponse> create(@AuthenticationPrincipal UserPrincipal me,
                                                 @PathVariable Long restaurantId,
                                                 @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(me.getId(), restaurantId, request));
    }

    /** Public (GET /api/restaurants/** is open in SecurityConfig), newest first. */
    @Operation(summary = "Reviews of a restaurant, newest first")
    @SecurityRequirements()
    @GetMapping
    public PageResponse<ReviewResponse> list(
            @PathVariable Long restaurantId,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return reviewService.getReviews(restaurantId, pageable);
    }
}
