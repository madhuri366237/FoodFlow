package com.foodflow.service;

import com.foodflow.dto.category.CategoryRequest;
import com.foodflow.dto.category.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> getAll();

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Long id, CategoryRequest request);

    void delete(Long id);
}
