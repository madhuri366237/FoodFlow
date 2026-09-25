package com.foodflow.repository;

import com.foodflow.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderId(Long orderId);

    /** Public list; the reviewer's name comes in the same query (no N+1 over reviewers). */
    @EntityGraph(attributePaths = "customer")
    Page<Review> findByRestaurantId(Long restaurantId, Pageable pageable);

    /*
     * Aggregation happens in PostgreSQL: one row comes back, however many reviews exist.
     * A JPQL constructor expression maps it straight into a record.
     */
    @Query("""
            select new com.foodflow.repository.RatingStats(coalesce(sum(r.rating), 0L), count(r))
            from Review r where r.restaurant.id = :restaurantId
            """)
    RatingStats ratingStats(@Param("restaurantId") Long restaurantId);
}
