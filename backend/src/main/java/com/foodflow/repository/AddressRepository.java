package com.foodflow.repository;

import com.foodflow.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    /** Default address first, then oldest first. */
    List<Address> findByUserIdOrderByDefaultAddressDescIdAsc(Long userId);

    /*
     * Ownership-scoped lookup. The service uses THIS instead of findById, so a customer
     * asking for someone else's address id gets "not found" - the WHERE clause itself
     * enforces ownership, and the response doesn't reveal that the id exists.
     */
    Optional<Address> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /*
     * Must run BEFORE marking another address as default: the partial unique index
     * uk_addresses_one_default_per_user is checked row by row, so setting the new default
     * first would briefly create two defaults and fail.
     * flushAutomatically: pending changes are written before this bulk UPDATE runs.
     * clearAutomatically: the persistence context is cleared afterwards, because a bulk
     * UPDATE bypasses it and any cached Address objects would be stale.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Address a set a.defaultAddress = false "
            + "where a.user.id = :userId and a.defaultAddress = true")
    int clearDefaultForUser(@Param("userId") Long userId);
}
