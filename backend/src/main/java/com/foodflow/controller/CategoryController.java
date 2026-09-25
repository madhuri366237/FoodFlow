package com.foodflow.controller;

import com.foodflow.dto.category.CategoryResponse;
import com.foodflow.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public: the home page's category chips and the owner's "category" dropdown. */
@Tag(name = "Categories")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "List all food categories")
    @SecurityRequirements()
    @GetMapping
    public List<CategoryResponse> all() {
        return categoryService.getAll();
    }
}
