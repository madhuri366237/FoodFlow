package com.foodflow.controller;

import com.foodflow.ApiIntegrationTestBase;
import com.foodflow.dto.category.CategoryRequest;
import com.foodflow.entity.Category;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryApiIntegrationTest extends ApiIntegrationTestBase {

    private User admin;
    private User owner;

    @BeforeEach
    void setUp() {
        admin = createUser("admin@example.com", Role.ADMIN);
        owner = createUser("owner@example.com", Role.RESTAURANT_OWNER);
    }

    private ResultActions createCategory(User user, String name) throws Exception {
        return mockMvc.perform(post("/api/admin/categories").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(new CategoryRequest(name, null, null))));
    }

    @Test
    void anyoneCanListCategories() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(hasItems("Biryani", "Pizza", "Beverages")));
    }

    @Test
    void adminCreatesCategory() throws Exception {
        createCategory(admin, "Momos").andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Momos"));
    }

    @Test
    void duplicateCategoryIsRejectedIgnoringCase() throws Exception {
        createCategory(admin, "PIZZA").andExpect(status().isConflict());
    }

    @Test
    void nonAdminCannotCreateCategory() throws Exception {
        createCategory(owner, "Momos").andExpect(status().isForbidden());
    }

    @Test
    void categoryInUseCannotBeDeleted() throws Exception {
        createMenuItem(createRestaurant(owner, "Spice Hub", "4.0", true), "Biryani", "Chicken Biryani", "250.00", true);

        mockMvc.perform(delete("/api/admin/categories/" + category("Biryani").getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void unusedCategoryCanBeDeleted() throws Exception {
        Category unused = categoryRepository.save(new Category("Soups", null, null));

        mockMvc.perform(delete("/api/admin/categories/" + unused.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
    }
}
