package com.foodflow.util;

import com.foodflow.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Makes client-supplied paging safe and deterministic.
 */
public final class PageableUtils {

    private PageableUtils() {
    }

    /**
     * 1. Whitelist: ?sort=owner.passwordHash would otherwise be passed straight to JPA.
     *    Unknown properties would crash with a 500, and sorting by a hidden column leaks
     *    information about it through the result order.
     * 2. Tie-breaker: "ORDER BY rating DESC" alone is not a total order. Rows with equal
     *    ratings can come back in a different order on each query, so an item can appear
     *    on both page 1 and page 2 while another never appears. Appending "id ASC" makes
     *    the order unique and stable.
     */
    public static Pageable sanitize(Pageable pageable, Set<String> allowedProperties) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '%s'. Allowed: %s"
                        .formatted(order.getProperty(), allowedProperties));
            }
        }
        Sort stableSort = pageable.getSort().and(Sort.by(Sort.Direction.ASC, "id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), stableSort);
    }
}
