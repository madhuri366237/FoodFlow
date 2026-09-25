package com.foodflow.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable JSON shape for paginated results.
 *
 * <p>Spring's Page/PageImpl is not returned directly: its JSON layout is an internal detail
 * that can change between Spring versions (Spring Data warns about exactly this). Our own
 * record makes the API contract explicit.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
