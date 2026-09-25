package com.foodflow.repository;

import com.foodflow.entity.MenuItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * N+1 prevention: MenuItem.category is LAZY. A menu response shows each item's category
 * name, so a plain findByRestaurantId would run 1 query for the items plus 1 per
 * distinct category (N+1). {@code @EntityGraph} makes Hibernate add a JOIN and load
 * items and categories in ONE statement, for these queries only; the mapping stays LAZY
 * everywhere else.
 */
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    /** Full menu, including unavailable items: the owner's view. */
    @EntityGraph(attributePaths = "category")
    List<MenuItem> findByRestaurantIdOrderByNameAsc(Long restaurantId);

    /** Orderable items only: the customer's view. */
    @EntityGraph(attributePaths = "category")
    List<MenuItem> findByRestaurantIdAndAvailableTrueOrderByNameAsc(Long restaurantId);

    /*
     * Loads the item AND its restaurant in one query, so the service can compare
     * item.getRestaurant().getOwner().getId() with the caller. The owner itself stays a
     * proxy: its id is the restaurants.owner_id column and needs no extra query.
     */
    @EntityGraph(attributePaths = {"restaurant", "category"})
    Optional<MenuItem> findWithRestaurantById(Long id);

    boolean existsByRestaurantIdAndNameIgnoreCase(Long restaurantId, String name);

    /** Same duplicate check for updates: ignore the item being renamed. */
    boolean existsByRestaurantIdAndNameIgnoreCaseAndIdNot(Long restaurantId, String name, Long id);

    boolean existsByCategoryId(Long categoryId);
}
