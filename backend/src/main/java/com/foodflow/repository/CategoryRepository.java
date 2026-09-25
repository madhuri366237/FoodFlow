package com.foodflow.repository;

import com.foodflow.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Categories are a small, bounded list (tens of rows), so no pagination is needed.
    List<Category> findAllByOrderByNameAsc();

    // "...IgnoreCase" generates upper(name) = upper(?). The unique index on lower(name) is what
    // actually guarantees uniqueness; this check just gives a friendly error before the INSERT.
    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
