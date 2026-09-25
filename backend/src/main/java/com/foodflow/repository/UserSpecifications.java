package com.foodflow.repository;

import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/** Filters for the admin's user list. */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> roleIs(Role role) {
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    public static Specification<User> enabledIs(boolean enabled) {
        return (root, query, cb) -> cb.equal(root.get("enabled"), enabled);
    }

    /** Name or email contains the keyword, case-insensitively; LIKE wildcards are escaped. */
    public static Specification<User> matches(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + RestaurantSpecifications.escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
            return cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
                    cb.like(root.get("email"), pattern, '\\')); // emails are stored lower-case
        };
    }
}
