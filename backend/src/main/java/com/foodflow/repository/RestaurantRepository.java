package com.foodflow.repository;

import com.foodflow.entity.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

/**
 * JpaSpecificationExecutor lets Phase 4 build the search query (keyword, category,
 * rating, open/closed) dynamically from whichever filters the client sent,
 * with paging and sorting done by PostgreSQL.
 */
public interface RestaurantRepository extends JpaRepository<Restaurant, Long>,
        JpaSpecificationExecutor<Restaurant> {

    Page<Restaurant> findByOwnerId(Long ownerId, Pageable pageable);

    /** Public lookup: customers must not see restaurants an admin has deactivated. */
    Optional<Restaurant> findByIdAndActiveTrue(Long id);

    /**
     * SELECT ... FOR UPDATE on one restaurant. Used to serialise rating recalculations, so two
     * reviews arriving together can't each compute an average that misses the other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Restaurant r where r.id = :id")
    Optional<Restaurant> findByIdForUpdate(@Param("id") Long id);
}
