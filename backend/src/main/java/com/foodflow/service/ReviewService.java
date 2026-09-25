package com.foodflow.service;

import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.review.ReviewRequest;
import com.foodflow.dto.review.ReviewResponse;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    ReviewResponse create(Long customerId, Long restaurantId, ReviewRequest request);

    PageResponse<ReviewResponse> getReviews(Long restaurantId, Pageable pageable);
}
