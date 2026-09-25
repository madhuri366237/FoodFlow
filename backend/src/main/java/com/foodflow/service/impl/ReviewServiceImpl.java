package com.foodflow.service.impl;

import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.review.ReviewRequest;
import com.foodflow.dto.review.ReviewResponse;
import com.foodflow.entity.Order;
import com.foodflow.entity.OrderStatus;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Review;
import com.foodflow.exception.BadRequestException;
import com.foodflow.exception.ConflictException;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.OrderRepository;
import com.foodflow.repository.RatingStats;
import com.foodflow.repository.RestaurantRepository;
import com.foodflow.repository.ReviewRepository;
import com.foodflow.service.ReviewService;
import com.foodflow.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "rating");

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;

    /*
     * One transaction:
     *   1. validate the order (yours, this restaurant, DELIVERED, not reviewed yet)
     *   2. LOCK the restaurant row             SELECT ... FOR UPDATE
     *   3. INSERT the review
     *   4. SUM/COUNT all its reviews           (now includes the new one)
     *   5. UPDATE restaurants.rating / rating_count
     *
     * Why lock in step 2: without it, two reviews committed at the same moment each run step 4
     * before seeing the other's INSERT (READ COMMITTED only shows committed rows). Both write an
     * average that is missing one review, and the second write wins. With the lock, the second
     * transaction waits at step 2 until the first commits; its step 4 then sees both reviews.
     *
     * Updating through the entity also bumps Restaurant.@Version: if the owner saves a stale edit
     * form at the same time, they get 409 instead of silently overwriting the new rating.
     */
    @Override
    @Transactional
    public ReviewResponse create(Long customerId, Long restaurantId, ReviewRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .filter(o -> o.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> ResourceNotFoundException.of("Order", request.orderId()));
        if (!order.getRestaurant().getId().equals(restaurantId)) {
            throw new BadRequestException("Order " + order.getId() + " was not placed at this restaurant");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ConflictException("ORDER_NOT_DELIVERED",
                    "You can review an order once it has been delivered (current status: " + order.getStatus() + ")");
        }
        if (reviewRepository.existsByOrderId(order.getId())) {
            throw alreadyReviewed();
        }

        Restaurant restaurant = restaurantRepository.findByIdForUpdate(restaurantId)
                .orElseThrow(() -> ResourceNotFoundException.of("Restaurant", restaurantId));

        Review review;
        try {
            // saveAndFlush: the INSERT must reach the database BEFORE the SUM/COUNT query below.
            // It also surfaces a lost race on uk_reviews_order right here.
            review = reviewRepository.saveAndFlush(new Review(order.getCustomer(), restaurant, order,
                    request.rating(), blankToNull(request.comment())));
        } catch (DataIntegrityViolationException e) {
            throw alreadyReviewed();
        }

        RatingStats stats = reviewRepository.ratingStats(restaurantId);
        restaurant.updateRating(stats.average(), Math.toIntExact(stats.count()));
        restaurantRepository.flush();

        return ReviewResponse.from(review);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getReviews(Long restaurantId, Pageable pageable) {
        if (restaurantRepository.findByIdAndActiveTrue(restaurantId).isEmpty()) {
            throw ResourceNotFoundException.of("Restaurant", restaurantId);
        }
        return PageResponse.from(reviewRepository
                .findByRestaurantId(restaurantId, PageableUtils.sanitize(pageable, SORTABLE))
                .map(ReviewResponse::from));
    }


    private static ConflictException alreadyReviewed() {
        return new ConflictException("ALREADY_REVIEWED", "You have already reviewed this order");
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
