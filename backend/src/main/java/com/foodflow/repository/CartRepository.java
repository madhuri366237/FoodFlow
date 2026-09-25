package com.foodflow.repository;

import com.foodflow.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /*
     * Creates the cart if the user doesn't have one yet, safely under concurrency.
     * "Check if exists, then INSERT" has a race: two parallel first-time requests both see
     * "no cart" and both insert, and one fails on uk_carts_user. PostgreSQL's
     * ON CONFLICT DO NOTHING makes the check-and-insert a single atomic statement.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO carts (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING",
            nativeQuery = true)
    void createIfAbsent(@Param("userId") Long userId);

    /*
     * SELECT ... FOR UPDATE: locks this cart's row until the transaction ends. Every cart
     * write takes this lock first, so two concurrent requests for the same cart run one
     * after the other. Without it, two "add item" calls from different restaurants on an
     * empty cart could both see "cart is empty" and both succeed, producing a mixed cart.
     * Only this one row is locked, so other customers are never blocked.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.user.id = :userId")
    Optional<Cart> findByUserIdForUpdate(@Param("userId") Long userId);

    /*
     * Read path: the cart, its restaurant, its lines and each line's dish, in ONE query.
     * (Hibernate 6 removes the duplicate Cart rows the collection join produces.)
     */
    @Query("""
            select c from Cart c
            left join fetch c.restaurant
            left join fetch c.items i
            left join fetch i.menuItem
            where c.user.id = :userId
            """)
    Optional<Cart> findWithItemsByUserId(@Param("userId") Long userId);
}
