package com.foodflow.controller;

import com.foodflow.ApiIntegrationTestBase;
import com.foodflow.dto.restaurant.RestaurantRequest;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Restaurant writes: who may create, update, toggle and delete. */
class RestaurantManagementIntegrationTest extends ApiIntegrationTestBase {

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User otherOwner;
    private User customer;
    private User admin;
    private Restaurant ownRestaurant;

    @BeforeEach
    void setUp() {
        owner = createUser("owner@example.com", Role.RESTAURANT_OWNER);
        otherOwner = createUser("other-owner@example.com", Role.RESTAURANT_OWNER);
        customer = createUser("customer@example.com", Role.CUSTOMER);
        admin = createUser("admin@example.com", Role.ADMIN);
        ownRestaurant = createRestaurant(owner, "Spice Hub", "4.0", true);
    }

    private static RestaurantRequest request(String name) {
        return new RestaurantRequest(name, "Great food", "12 MG Road, Bengaluru", "9876543210",
                "https://img.example.com/r.jpg");
    }

    private ResultActions postRestaurant(User user, Object body) throws Exception {
        var builder = post("/api/restaurants").contentType(MediaType.APPLICATION_JSON).content(json(body));
        if (user != null) {
            builder.header(HttpHeaders.AUTHORIZATION, bearer(user));
        }
        return mockMvc.perform(builder);
    }

    private ResultActions putRestaurant(User user, Long id, Object body) throws Exception {
        return mockMvc.perform(put("/api/restaurants/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    // ---------- create ----------

    @Test
    void anonymousCannotCreate() throws Exception {
        postRestaurant(null, request("Nope")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCreate() throws Exception {
        postRestaurant(customer, request("Nope"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void adminCannotCreateRestaurantsEither() throws Exception {
        postRestaurant(admin, request("Nope")).andExpect(status().isForbidden());
    }

    @Test
    void ownerCreatesRestaurantWith201AndLocation() throws Exception {
        ResultActions result = postRestaurant(owner, request("  Tandoor Tales  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Tandoor Tales"))
                .andExpect(jsonPath("$.rating").value(0))
                .andExpect(jsonPath("$.open").value(true));

        long id = body(result).get("id").asLong();
        result.andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/restaurants/" + id)));
        assertThat(restaurantRepository.findById(id).orElseThrow().getOwner().getId()).isEqualTo(owner.getId());
    }

    @Test
    void ratingAndOwnerInRequestBodyAreIgnored() throws Exception {
        String body = """
                {"name":"Sneaky","address":"1 Road","phone":"9876543210",
                 "rating":5.0,"ratingCount":999,"ownerId":%d,"active":false}
                """.formatted(otherOwner.getId());

        long id = body(mockMvc.perform(post("/api/restaurants").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(0))
                .andExpect(jsonPath("$.ratingCount").value(0))
                .andExpect(jsonPath("$.active").value(true))).get("id").asLong();

        Restaurant saved = restaurantRepository.findById(id).orElseThrow();
        assertThat(saved.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(saved.getRating()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void invalidRestaurantIsRejectedWithFieldErrors() throws Exception {
        postRestaurant(owner, new RestaurantRequest("", null, "", "12", "javascript:alert(1)"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.address").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.imageUrl").value("Image URL must be a valid http(s) URL"));
    }

    // ---------- update ----------

    @Test
    void ownerUpdatesOwnRestaurant() throws Exception {
        putRestaurant(owner, ownRestaurant.getId(), request("Spice Hub Deluxe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spice Hub Deluxe"));
    }

    @Test
    void ownerCannotUpdateAnotherOwnersRestaurant() throws Exception {
        putRestaurant(otherOwner, ownRestaurant.getId(), request("Hijacked"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only manage your own restaurants"));

        assertThat(restaurantRepository.findById(ownRestaurant.getId()).orElseThrow().getName()).isEqualTo("Spice Hub");
    }

    @Test
    void customerCannotUpdate() throws Exception {
        putRestaurant(customer, ownRestaurant.getId(), request("Hijacked")).andExpect(status().isForbidden());
    }

    @Test
    void updatingUnknownRestaurantReturns404() throws Exception {
        putRestaurant(owner, 999_999L, request("Ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void ownerTogglesOpenStatusButOtherOwnerCannot() throws Exception {
        mockMvc.perform(patch("/api/restaurants/" + ownRestaurant.getId() + "/open-status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"open\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));

        mockMvc.perform(patch("/api/restaurants/" + ownRestaurant.getId() + "/open-status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherOwner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"open\":true}"))
                .andExpect(status().isForbidden());
    }

    // ---------- delete ----------

    @Test
    void ownerDeletesOwnRestaurantAndItsMenu() throws Exception {
        createMenuItem(ownRestaurant, "Biryani", "Chicken Biryani", "250.00", true);
        // Simulate a fresh request: without clear(), the test's still-managed MenuItem would
        // reference the deleted restaurant and Hibernate would refuse the flush in Java.
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(delete("/api/restaurants/" + ownRestaurant.getId()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        assertThat(restaurantRepository.findById(ownRestaurant.getId())).isEmpty();
        assertThat(menuItemRepository.findByRestaurantIdOrderByNameAsc(ownRestaurant.getId())).isEmpty();
    }

    @Test
    void ownerCannotDeleteAnotherOwnersRestaurant() throws Exception {
        mockMvc.perform(delete("/api/restaurants/" + ownRestaurant.getId()).header(HttpHeaders.AUTHORIZATION, bearer(otherOwner)))
                .andExpect(status().isForbidden());

        assertThat(restaurantRepository.findById(ownRestaurant.getId())).isPresent();
    }

    // ---------- owner dashboard ----------

    @Test
    void ownerListsOnlyOwnRestaurantsIncludingInactive() throws Exception {
        Restaurant inactive = createRestaurant(owner, "Old Branch", "3.0", true);
        inactive.setActive(false);
        createRestaurant(otherOwner, "Not Mine", "4.0", true);

        mockMvc.perform(get("/api/owner/restaurants").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.contains("Old Branch", "Spice Hub")));
    }

    @Test
    void customerCannotUseOwnerDashboard() throws Exception {
        mockMvc.perform(get("/api/owner/restaurants").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden());
    }
}
