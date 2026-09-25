package com.foodflow.repository;

import com.foodflow.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Paging + fetching rule used below:
 * <ul>
 *   <li>Fetching to-ONE associations (restaurant, customer) with a page is fine: still one
 *       row per order, so LIMIT/OFFSET stay correct in SQL.</li>
 *   <li>Fetching a to-MANY collection (items) with a page is NOT: the SQL returns one row per
 *       item, so Hibernate can't apply LIMIT in SQL, loads EVERY matching order into memory and
 *       pages there (warning HHH90003004). List pages therefore load items lazily; the batch
 *       fetch size loads them for the whole page in one extra query.</li>
 * </ul>
 */
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /** "My orders": a page of the customer's orders, each with its restaurant. */
    @EntityGraph(attributePaths = {"restaurant", "customer"})
    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    /** Owner and admin lists: dynamic filters (OrderSpecifications) + restaurant and customer. */
    @Override
    @EntityGraph(attributePaths = {"restaurant", "customer"})
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    /*
     * Order details in one query: order + restaurant + customer + lines. The status history
     * (a second collection) is NOT fetched here. Join-fetching two List collections at once
     * multiplies rows (items x history) and Hibernate rejects it (MultipleBagFetchException);
     * history is loaded by one extra batched query instead.
     */
    @Query("""
            select o from Order o
            join fetch o.restaurant
            join fetch o.customer
            left join fetch o.items
            where o.id = :id
            """)
    Optional<Order> findDetailedById(@Param("id") Long id);

    boolean existsByRestaurantId(Long restaurantId);
}
