package com.foodflow.repository;

import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestaurantRepositoryTest extends RepositoryTestBase {

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Test
    void ownerIsLazyAndItsIdIsReadableWithoutLoadingIt() {
        User owner = persistUser("owner@example.com", Role.RESTAURANT_OWNER);
        Restaurant saved = persistRestaurant(owner, "Spice Hub");
        flushAndClear();

        Restaurant loaded = restaurantRepository.findById(saved.getId()).orElseThrow();

        // The owner is a proxy that holds only the foreign key...
        assertThat(Hibernate.isInitialized(loaded.getOwner())).isFalse();
        // ...and reading its id does not trigger a SELECT on users.
        assertThat(loaded.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(Hibernate.isInitialized(loaded.getOwner())).isFalse();
    }

    @Test
    void ownershipScopedLookupHidesOtherOwnersRestaurants() {
        User owner = persistUser("owner1@example.com", Role.RESTAURANT_OWNER);
        User otherOwner = persistUser("owner2@example.com", Role.RESTAURANT_OWNER);
        Restaurant restaurant = persistRestaurant(owner, "Pizza Point");
        persistRestaurant(otherOwner, "Burger Barn");
        flushAndClear();

        // (Write-access ownership checks live in RestaurantAccess and are tested there.)
        assertThat(restaurantRepository.findByOwnerId(owner.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(Restaurant::getId).containsExactly(restaurant.getId());
        assertThat(restaurantRepository.findByOwnerId(otherOwner.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(Restaurant::getName).containsExactly("Burger Barn");
    }

    @Test
    void deactivatedRestaurantIsHiddenFromPublicLookup() {
        Restaurant restaurant = persistRestaurant(persistUser("o@example.com", Role.RESTAURANT_OWNER), "Closed Down");
        restaurant.setActive(false);
        flushAndClear();

        assertThat(restaurantRepository.findByIdAndActiveTrue(restaurant.getId())).isEmpty();
        assertThat(restaurantRepository.findById(restaurant.getId())).isPresent();
    }

    @Test
    void concurrentUpdateIsDetectedByOptimisticLocking() {
        Restaurant restaurant = persistRestaurant(persistUser("lock@example.com", Role.RESTAURANT_OWNER), "Version Cafe");
        flushAndClear();

        // Request A reads the restaurant (version 0) and keeps it in memory.
        Restaurant staleCopy = restaurantRepository.findById(restaurant.getId()).orElseThrow();
        em.detach(staleCopy);

        // Request B updates the same row first and commits (version becomes 1).
        jdbc.update("update restaurants set name = 'Changed by B', version = version + 1 where id = ?",
                restaurant.getId());

        // Request A now tries to save its stale copy -> rejected instead of overwriting B's change.
        staleCopy.setName("Changed by A");
        assertThatThrownBy(() -> restaurantRepository.saveAndFlush(staleCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void ratingOutsideZeroToFiveIsRejectedByDatabase() {
        Restaurant restaurant = persistRestaurant(persistUser("r@example.com", Role.RESTAURANT_OWNER), "Five Star");
        restaurant.updateRating(new BigDecimal("5.5"), 1);

        // NUMERIC(2,1) can hold 5.5, so it is the CHECK constraint that stops it.
        assertThatThrownBy(() -> restaurantRepository.saveAndFlush(restaurant))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_restaurants_rating");
    }
}
