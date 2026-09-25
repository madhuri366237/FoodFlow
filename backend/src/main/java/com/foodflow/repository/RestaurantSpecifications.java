package com.foodflow.repository;

import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reusable WHERE-clause fragments for restaurant search. The service combines only the
 * filters the client actually sent, so one endpoint serves every combination
 * (keyword + rating, category + open, ...) without writing a query method for each.
 *
 * <p>Dish-based filters use {@code EXISTS (subquery)} rather than a JOIN. A JOIN to
 * menu_items would return one row per matching dish, so a restaurant with 3 biryanis
 * would appear 3 times. That breaks pagination (page 1 could be one restaurant repeated)
 * and needs a DISTINCT that makes the count query slower. EXISTS returns each restaurant
 * at most once and stops at the first matching dish.
 */
public final class RestaurantSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private RestaurantSpecifications() {
    }

    /** Customers only ever see restaurants the admin hasn't deactivated. */
    public static Specification<Restaurant> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    /** Admin filter: active or deactivated restaurants. */
    public static Specification<Restaurant> activeIs(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    public static Specification<Restaurant> isOpen(boolean open) {
        return (root, query, cb) -> cb.equal(root.get("open"), open);
    }

    public static Specification<Restaurant> ratingAtLeast(BigDecimal minRating) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("rating"), minRating);
    }

    /**
     * keyword=biryani matches restaurants whose NAME contains "biryani", OR that serve an
     * available DISH whose name contains it. Both sides compare lower(name) LIKE '%...%',
     * which the trigram indexes from V2 can serve.
     */
    public static Specification<Restaurant> matchesKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";

            Predicate nameMatches = cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE);

            Subquery<Integer> dish = query.subquery(Integer.class);
            Root<MenuItem> item = dish.from(MenuItem.class);
            dish.select(cb.literal(1)).where(
                    cb.equal(item.get("restaurant"), root),
                    cb.isTrue(item.get("available")),
                    cb.like(cb.lower(item.get("name")), pattern, LIKE_ESCAPE));

            return cb.or(nameMatches, cb.exists(dish));
        };
    }

    /**
     * Restaurants with at least one available dish matching ALL the given dish filters
     * (category and/or price range). One EXISTS for all of them means the SAME dish must be
     * a Biryani AND cost 100-200, not "some biryani" plus "some unrelated dish at 150".
     */
    public static Specification<Restaurant> servesDish(Long categoryId, BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            Subquery<Integer> dish = query.subquery(Integer.class);
            Root<MenuItem> item = dish.from(MenuItem.class);

            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(item.get("restaurant"), root));
            conditions.add(cb.isTrue(item.get("available")));
            if (categoryId != null) {
                conditions.add(cb.equal(item.get("category").get("id"), categoryId));
            }
            if (minPrice != null) {
                conditions.add(cb.greaterThanOrEqualTo(item.get("price"), minPrice));
            }
            if (maxPrice != null) {
                conditions.add(cb.lessThanOrEqualTo(item.get("price"), maxPrice));
            }

            dish.select(cb.literal(1)).where(conditions.toArray(Predicate[]::new));
            return cb.exists(dish);
        };
    }

    /*
     * In LIKE, '%' and '_' are wildcards. Without escaping, a search for "100%" or "_"
     * would match far more than the user typed. Values are still sent as bind parameters,
     * so this is about correct results; SQL injection is already impossible.
     */
    public static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
