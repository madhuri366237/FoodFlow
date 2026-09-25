package com.foodflow.controller;

import com.foodflow.ApiIntegrationTestBase;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminApiIntegrationTest extends ApiIntegrationTestBase {

    private User admin;
    private User customer;
    private User owner;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        admin = createUser("admin@example.com", Role.ADMIN);
        customer = createUser("asha@example.com", Role.CUSTOMER);
        createUser("ravi@example.com", Role.CUSTOMER);
        owner = createUser("owner@example.com", Role.RESTAURANT_OWNER);
        restaurant = createRestaurant(owner, "Biryani Blues", "4.5", true);
    }

    private ResultActions patchJson(User user, String url, String json) throws Exception {
        return mockMvc.perform(patch(url).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ---------- users ----------

    @Test
    void adminListsAndFiltersUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users?role=CUSTOMER").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());

        mockMvc.perform(get("/api/admin/users?keyword=ASHA").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.content[*].email").value(containsInAnyOrder("asha@example.com")));
    }

    @Test
    void disablingAUserLocksThemOutImmediately() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk());

        patchJson(admin, "/api/admin/users/" + customer.getId() + "/status", "{\"enabled\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // The same, still-unexpired token is refused on the very next request.
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isUnauthorized());

        patchJson(admin, "/api/admin/users/" + customer.getId() + "/status", "{\"enabled\":true}")
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void adminCannotDisableThemselves() throws Exception {
        patchJson(admin, "/api/admin/users/" + admin.getId() + "/status", "{\"enabled\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot disable your own account"));
    }

    @Test
    void nonAdminsCannotManageUsers() throws Exception {
        patchJson(owner, "/api/admin/users/" + customer.getId() + "/status", "{\"enabled\":false}")
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden());
    }

    // ---------- restaurants ----------

    @Test
    void deactivatedRestaurantDisappearsForCustomersButNotForAdminOrOwner() throws Exception {
        patchJson(admin, "/api/admin/restaurants/" + restaurant.getId() + "/status", "{\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.ownerEmail").value("owner@example.com"));

        mockMvc.perform(get("/api/restaurants/" + restaurant.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/restaurants")).andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/admin/restaurants?active=false").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ownerName").value("Test RESTAURANT_OWNER"));
        mockMvc.perform(get("/api/owner/restaurants").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.content[0].active").value(false));
    }

    @Test
    void ownerCannotReactivateOwnRestaurant() throws Exception {
        patchJson(owner, "/api/admin/restaurants/" + restaurant.getId() + "/status", "{\"active\":true}")
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidStatusBodyIs400() throws Exception {
        patchJson(admin, "/api/admin/restaurants/" + restaurant.getId() + "/status", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.active").value("active is required"));
    }

    // ---------- orders ----------

    @Test
    void adminSeesAllOrdersWithFilters() throws Exception {
        mockMvc.perform(get("/api/admin/orders?status=PLACED").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/admin/orders?status=NOPE").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
    }
}
