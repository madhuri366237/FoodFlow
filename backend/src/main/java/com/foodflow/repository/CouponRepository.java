package com.foodflow.repository;

import com.foodflow.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /** Pass the code through Coupon.normalizeCode first. */
    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    /** "Offers for you": active and not yet expired. */
    List<Coupon> findByActiveTrueAndExpiresAtAfterOrderByExpiresAtAsc(Instant now);

    /*
     * Redeems one use ATOMICALLY: the check and the increment are the same SQL statement.
     *
     *   UPDATE coupons SET used_count = used_count + 1
     *   WHERE id = ? AND active AND expires_at > ? AND (usage_limit IS NULL OR used_count < usage_limit)
     *
     * Returns 1 if a use was taken, 0 if none was left. Under concurrency, PostgreSQL row-locks
     * the coupon for the first UPDATE; the second waits, then re-checks the WHERE against the
     * NEW used_count. So with 1 use left, exactly one of two simultaneous checkouts gets it.
     * (The naive "read usedCount, check it in Java, save usedCount+1" lets both succeed.)
     *
     * No clearAutomatically: clearing would detach the cart and order entities in the middle of
     * checkout. The in-memory Coupon's usedCount is stale afterwards, and nothing reads it.
     */
    @Modifying
    @Query("""
            update Coupon c set c.usedCount = c.usedCount + 1
            where c.id = :id and c.active = true and c.expiresAt > :now
              and (c.usageLimit is null or c.usedCount < c.usageLimit)
            """)
    int tryRedeem(@Param("id") Long id, @Param("now") Instant now);

    /** Gives a use back (order cancelled). Never below zero. */
    @Modifying
    @Query("update Coupon c set c.usedCount = c.usedCount - 1 where c.id = :id and c.usedCount > 0")
    int release(@Param("id") Long id);
}
