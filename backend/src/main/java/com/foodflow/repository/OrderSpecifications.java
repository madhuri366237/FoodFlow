package com.foodflow.repository;

import com.foodflow.entity.Order;
import com.foodflow.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

/** Filters for the owner's (and later the admin's) order lists. */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    /** Orders of any restaurant owned by this user: the owner's hard boundary. */
    public static Specification<Order> restaurantOwnedBy(Long ownerId) {
        return (root, query, cb) -> cb.equal(root.get("restaurant").get("owner").get("id"), ownerId);
    }

    public static Specification<Order> restaurantIs(Long restaurantId) {
        return (root, query, cb) -> cb.equal(root.get("restaurant").get("id"), restaurantId);
    }

    public static Specification<Order> statusIs(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
