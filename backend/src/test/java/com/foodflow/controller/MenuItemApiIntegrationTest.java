package com.foodflow.controller;

import com.foodflow.ApiIntegrationTestBase;
import com.foodflow.dto.menu.MenuItemRequest;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Menu writes: only the owner of the restaurant may add, change or remove its dishes. */
class MenuItemApiIntegrationTest extends ApiIntegrationTestBase {

    private User owner;
    private User otherOwner;
    private User customer;
    private Restaurant restaurant;
    private MenuItem dish;

    @BeforeEach
    void setUp() {
        owner = createUser("owner@example.com", Role.RESTAURANT_OWNER);
        otherOwner = createUser("other-owner@example.com", Role.RESTAURANT_OWNER);
        customer = createUser("customer@example.com", Role.CUSTOMER);
        restaurant = createRestaurant(owner, "Spice Hub", "4.0", true);
        dish = createMenuItem(restaurant, "Biryani", "Chicken Biryani", "250.00", true);
    }

    private MenuItemRequest request(String name, String price) {
        return new MenuItemRequest(category("Starters").getId(), name, "Tasty", new BigDecimal(price), null, null);
    }

    private ResultActions createDish(User user, Long restaurantId, Object body) throws Exception {
        return mockMvc.perform(post("/api/restaurants/" + restaurantId + "/menu-items")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions updateDish(User user, Long itemId, Object body) throws Exception {
        return mockMvc.perform(put("/api/menu-items/" + itemId).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    // ---------- create ----------

    @Test
    void ownerAddsDishToOwnRestaurant() throws Exception {
        ResultActions result = createDish(owner, restaurant.getId(), request("Paneer Tikka", "220.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Paneer Tikka"))
                .andExpect(jsonPath("$.categoryName").value("Starters"))
                .andExpect(jsonPath("$.restaurantId").value(restaurant.getId()))
                .andExpect(jsonPath("$.available").value(true));

        long id = body(result).get("id").asLong();
        result.andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/menu-items/" + id)));
    }

    @Test
    void ownerCannotAddDishToAnotherOwnersRestaurant() throws Exception {
        createDish(otherOwner, restaurant.getId(), request("Intruder Dish", "99.00"))
                .andExpect(status().isForbidden());

        assertThat(menuItemRepository.existsByRestaurantIdAndNameIgnoreCase(restaurant.getId(), "Intruder Dish")).isFalse();
    }

    @Test
    void customerCannotAddDish() throws Exception {
        createDish(customer, restaurant.getId(), request("Customer Dish", "99.00")).andExpect(status().isForbidden());
    }

    @Test
    void duplicateDishNameIsRejectedIgnoringCase() throws Exception {
        createDish(owner, restaurant.getId(), request("CHICKEN BIRYANI", "260.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This restaurant already has a dish named 'CHICKEN BIRYANI'"));
    }

    @Test
    void invalidPricesAreRejected() throws Exception {
        createDish(owner, restaurant.getId(), request("Free Food", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").value("Price must be greater than 0"));
        createDish(owner, restaurant.getId(), request("Negative", "-10"))
                .andExpect(status().isBadRequest());
        createDish(owner, restaurant.getId(), request("Fractional", "10.555"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").value("Price must have at most 2 decimal places"));
    }

    @Test
    void unknownCategoryIsRejected() throws Exception {
        createDish(owner, restaurant.getId(),
                new MenuItemRequest(999_999L, "Mystery", null, new BigDecimal("100.00"), null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category 999999 does not exist"));
    }

    // ---------- update / availability / delete ----------

    @Test
    void ownerUpdatesOwnDish() throws Exception {
        updateDish(owner, dish.getId(), request("Chicken Biryani", "275.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(275.50))
                .andExpect(jsonPath("$.categoryName").value("Starters"));
    }

    @Test
    void ownerCannotUpdateAnotherOwnersDish() throws Exception {
        updateDish(otherOwner, dish.getId(), request("Chicken Biryani", "1.00"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only manage menu items of your own restaurants"));

        assertThat(menuItemRepository.findById(dish.getId()).orElseThrow().getPrice()).isEqualByComparingTo("250.00");
    }

    @Test
    void customerCannotUpdateDish() throws Exception {
        updateDish(customer, dish.getId(), request("Chicken Biryani", "1.00")).andExpect(status().isForbidden());
    }

    @Test
    void renamingToAnExistingDishNameIsRejected() throws Exception {
        MenuItem other = createMenuItem(restaurant, "Desserts", "Gulab Jamun", "90.00", true);

        updateDish(owner, other.getId(), request("chicken biryani", "90.00")).andExpect(status().isConflict());
    }

    @Test
    void unavailableDishDisappearsFromPublicMenuButStaysInOwnerMenu() throws Exception {
        mockMvc.perform(patch("/api/menu-items/" + dish.getId() + "/availability")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(get("/api/restaurants/" + restaurant.getId() + "/menu"))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/owner/restaurants/" + restaurant.getId() + "/menu")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].available").value(false));
    }

    @Test
    void ownerCannotReadAnotherOwnersFullMenu() throws Exception {
        mockMvc.perform(get("/api/owner/restaurants/" + restaurant.getId() + "/menu")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherOwner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerDeletesOwnDishButOtherOwnerCannot() throws Exception {
        mockMvc.perform(delete("/api/menu-items/" + dish.getId()).header(HttpHeaders.AUTHORIZATION, bearer(otherOwner)))
                .andExpect(status().isForbidden());
        assertThat(menuItemRepository.findById(dish.getId())).isPresent();

        mockMvc.perform(delete("/api/menu-items/" + dish.getId()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
        assertThat(menuItemRepository.findById(dish.getId())).isEmpty();
    }

    @Test
    void unknownDishReturns404() throws Exception {
        updateDish(owner, 999_999L, request("Ghost", "10.00")).andExpect(status().isNotFound());
    }
}
