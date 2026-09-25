package com.foodflow.controller;

import com.foodflow.ApiIntegrationTestBase;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.repository.CartRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartApiIntegrationTest extends ApiIntegrationTestBase {

    @Autowired private CartRepository cartRepository;
    @Autowired private EntityManager entityManager;

    private User customer;
    private User otherCustomer;
    private User owner;
    private Restaurant biryaniBlues;
    private Restaurant pizzaPoint;
    private MenuItem biryani;   // 250.00
    private MenuItem raita;     //  40.00
    private MenuItem pizza;     // 199.00

    @BeforeEach
    void setUp() {
        customer = createUser("customer@example.com", Role.CUSTOMER);
        otherCustomer = createUser("other@example.com", Role.CUSTOMER);
        owner = createUser("owner@example.com", Role.RESTAURANT_OWNER);
        biryaniBlues = createRestaurant(owner, "Biryani Blues", "4.5", true);
        pizzaPoint = createRestaurant(owner, "Pizza Point", "4.0", true);
        biryani = createMenuItem(biryaniBlues, "Biryani", "Chicken Biryani", "250.00", true);
        raita = createMenuItem(biryaniBlues, "Starters", "Raita", "40.00", true);
        pizza = createMenuItem(pizzaPoint, "Pizza", "Veg Pizza", "199.00", true);
    }

    private ResultActions add(User user, Long menuItemId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/cart/items").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuItemId\":%d,\"quantity\":%d}".formatted(menuItemId, quantity)));
    }

    private ResultActions getCart(User user) throws Exception {
        return mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private long firstLineId(ResultActions result) throws Exception {
        return body(result).get("items").get(0).get("id").asLong();
    }

    // ---------- read ----------

    @Test
    void newCustomerHasEmptyCartAndGetCreatesNoRow() throws Exception {
        getCart(customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.subtotal").value(0))
                .andExpect(jsonPath("$.checkoutReady").value(false));

        assertThat(cartRepository.findWithItemsByUserId(customer.getId())).isEmpty();
    }

    // ---------- add item ----------

    @Test
    void addItemComputesPriceFromDatabaseIgnoringClientPrice() throws Exception {
        // A malicious client sends its own price and total; both are ignored.
        mockMvc.perform(post("/api/cart/items").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":%d,\"quantity\":2,\"price\":1.00,\"lineTotal\":2.00}"
                                .formatted(biryani.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(biryaniBlues.getId()))
                .andExpect(jsonPath("$.restaurantName").value("Biryani Blues"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Chicken Biryani"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(250.00))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(500.00))
                .andExpect(jsonPath("$.subtotal").value(500.00))
                .andExpect(jsonPath("$.checkoutReady").value(true));
    }

    @Test
    void addingSameDishAgainIncreasesQuantityOfOneLine() throws Exception {
        add(customer, biryani.getId(), 2);
        add(customer, raita.getId(), 1);

        add(customer, biryani.getId(), 3)
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.totalQuantity").value(6))
                .andExpect(jsonPath("$.subtotal").value(5 * 250 + 40));
    }

    @Test
    void quantityLimitsAreEnforced() throws Exception {
        add(customer, biryani.getId(), 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").value("quantity must be at least 1"));
        add(customer, biryani.getId(), 21).andExpect(status().isBadRequest());
        // A fractional quantity is rejected, not silently truncated to 2.
        mockMvc.perform(post("/api/cart/items").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":%d,\"quantity\":2.7}".formatted(biryani.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for quantity"));

        add(customer, biryani.getId(), 15).andExpect(status().isOk());
        // 15 + 10 = 25 > 20: rejected, and the line keeps 15.
        add(customer, biryani.getId(), 10).andExpect(status().isBadRequest());
        getCart(customer).andExpect(jsonPath("$.items[0].quantity").value(15));
    }

    // ---------- the one-restaurant rule ----------

    @Test
    void mixingRestaurantsIsRejectedAndCartIsUnchanged() throws Exception {
        add(customer, biryani.getId(), 1).andExpect(status().isOk());

        add(customer, pizza.getId(), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CART_RESTAURANT_MISMATCH"))
                .andExpect(jsonPath("$.message").value(
                        "Your cart contains items from Biryani Blues. Clear the cart before adding items from Pizza Point."));

        getCart(customer)
                .andExpect(jsonPath("$.restaurantName").value("Biryani Blues"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Chicken Biryani"));
    }

    @Test
    void afterClearingCartCustomerCanSwitchRestaurant() throws Exception {
        add(customer, biryani.getId(), 1);

        mockMvc.perform(delete("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isNoContent());

        add(customer, pizza.getId(), 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Pizza Point"));
    }

    @Test
    void removingLastItemFreesTheCartForAnotherRestaurant() throws Exception {
        long lineId = firstLineId(add(customer, biryani.getId(), 1));

        mockMvc.perform(delete("/api/cart/items/" + lineId).header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.restaurantId").doesNotExist());

        add(customer, pizza.getId(), 1).andExpect(status().isOk());
    }

    @Test
    void cartsOfDifferentCustomersAreIndependent() throws Exception {
        add(customer, biryani.getId(), 1).andExpect(status().isOk());
        add(otherCustomer, pizza.getId(), 1).andExpect(status().isOk());
    }

    // ---------- update / remove ----------

    @Test
    void updateQuantitySetsNewValueAndRecalculates() throws Exception {
        long lineId = firstLineId(add(customer, biryani.getId(), 1));

        mockMvc.perform(put("/api/cart/items/" + lineId).header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(4))
                .andExpect(jsonPath("$.subtotal").value(1000.00));
    }

    @Test
    void updateQuantityToZeroIsRejected() throws Exception {
        long lineId = firstLineId(add(customer, biryani.getId(), 1));

        mockMvc.perform(put("/api/cart/items/" + lineId).header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerCannotTouchAnotherCustomersCartLine() throws Exception {
        long lineId = firstLineId(add(customer, biryani.getId(), 1));

        // 404, not 403: the other customer doesn't even learn the line exists.
        mockMvc.perform(put("/api/cart/items/" + lineId).header(HttpHeaders.AUTHORIZATION, bearer(otherCustomer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":9}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/cart/items/" + lineId).header(HttpHeaders.AUTHORIZATION, bearer(otherCustomer)))
                .andExpect(status().isNotFound());

        getCart(customer).andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    // ---------- availability and live prices ----------

    @Test
    void unavailableDishCannotBeAdded() throws Exception {
        biryani.setAvailable(false);

        add(customer, biryani.getId(), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ITEM_UNAVAILABLE"));
    }

    @Test
    void closedRestaurantRejectsNewItems() throws Exception {
        biryaniBlues.setOpen(false);

        add(customer, biryani.getId(), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("RESTAURANT_CLOSED"));
    }

    @Test
    void unknownOrInactiveDishReturns404() throws Exception {
        add(customer, 999_999L, 1).andExpect(status().isNotFound());

        biryaniBlues.setActive(false);
        add(customer, biryani.getId(), 1).andExpect(status().isNotFound());
    }

    @Test
    void priceChangeByOwnerIsReflectedImmediately() throws Exception {
        add(customer, biryani.getId(), 2).andExpect(jsonPath("$.subtotal").value(500.00));

        biryani.setPrice(new BigDecimal("300.00"));
        entityManager.flush();

        getCart(customer)
                .andExpect(jsonPath("$.items[0].unitPrice").value(300.00))
                .andExpect(jsonPath("$.subtotal").value(600.00));
    }

    @Test
    void dishThatBecomesUnavailableIsFlaggedAndBlocksCheckout() throws Exception {
        add(customer, biryani.getId(), 1);
        add(customer, raita.getId(), 2);

        raita.setAvailable(false);
        entityManager.flush();

        getCart(customer)
                .andExpect(jsonPath("$.items[1].available").value(false))
                .andExpect(jsonPath("$.subtotal").value(250.00))   // raita excluded
                .andExpect(jsonPath("$.checkoutReady").value(false));
    }

    @Test
    void readingACartWithSeveralLinesIsOneQueryPlusAuthentication() throws Exception {
        add(customer, biryani.getId(), 1);
        add(customer, raita.getId(), 1);
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        getCart(customer).andExpect(jsonPath("$.items", hasSize(2)));

        // 1: JwtFilter loads the user by id (every authenticated request does this, Phase 3).
        // 1: cart + restaurant + lines + dishes in a single JOIN FETCH query. No N+1.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1 + 1);
    }

    // ---------- who may use a cart ----------

    @Test
    void onlyCustomersHaveCarts() throws Exception {
        mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        getCart(owner).andExpect(status().isForbidden());
        add(owner, biryani.getId(), 1).andExpect(status().isForbidden());
    }
}
