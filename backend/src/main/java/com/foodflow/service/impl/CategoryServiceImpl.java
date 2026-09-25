package com.foodflow.service.impl;

import com.foodflow.dto.category.CategoryRequest;
import com.foodflow.dto.category.CategoryResponse;
import com.foodflow.entity.Category;
import com.foodflow.exception.ConflictException;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.CategoryRepository;
import com.foodflow.repository.MenuItemRepository;
import com.foodflow.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Category '" + name + "' already exists");
        }
        return CategoryResponse.from(categoryRepository.save(
                new Category(name, request.description(), request.imageUrl())));
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Category '" + name + "' already exists");
        }
        category.setName(name);
        category.setDescription(request.description());
        category.setImageUrl(request.imageUrl());
        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));
        // Friendly message; the RESTRICT foreign key would block the delete anyway.
        if (menuItemRepository.existsByCategoryId(id)) {
            throw new ConflictException("Category '" + category.getName() + "' is used by menu items and cannot be deleted");
        }
        categoryRepository.delete(category);
    }
}
