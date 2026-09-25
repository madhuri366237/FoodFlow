package com.foodflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A restaurant. Relationship: many restaurants -> one owner (a RESTAURANT_OWNER user).
 *
 * <p>There is intentionally no {@code List<MenuItem> menuItems} field. A menu is always
 * loaded with {@code menuItemRepository.findByRestaurantId...}, which can be filtered,
 * sorted and fetched in one query. See the Phase 2 notes.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "restaurants")
public class Restaurant extends BaseEntity {

    /*
     * LAZY: listing 20 restaurants must not fire 20 extra "select user" queries.
     * restaurant.getOwner().getId() does NOT trigger a query - the proxy already
     * knows the foreign key - which is all ownership checks need.
     */
    @Setter(AccessLevel.NONE) // ownership never changes through the API
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // Changed only via updateRating(), never by a request DTO.
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating = BigDecimal.ZERO;

    @Setter(AccessLevel.NONE)
    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "is_open", nullable = false)
    private boolean open = true;

    @Column(nullable = false)
    private boolean active = true;

    /*
     * Optimistic locking: every UPDATE runs "... WHERE id = ? AND version = ?" and
     * increments version. If two requests edit the same restaurant concurrently
     * (owner editing details while a new review updates the rating), the second
     * commit matches 0 rows and fails with an OptimisticLockException instead of
     * silently overwriting the first change (a "lost update").
     */
    @Setter(AccessLevel.NONE)
    @Version
    @Column(nullable = false)
    private Long version;

    public Restaurant(User owner, String name, String description, String address,
                      String phone, String imageUrl) {
        this.owner = owner;
        this.name = name;
        this.description = description;
        this.address = address;
        this.phone = phone;
        this.imageUrl = imageUrl;
    }

    /** Called by the review service (Phase 9) after recalculating the average. */
    public void updateRating(BigDecimal averageRating, int reviewCount) {
        this.rating = averageRating;
        this.ratingCount = reviewCount;
    }
}
