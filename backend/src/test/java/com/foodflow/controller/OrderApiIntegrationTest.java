package com.foodflow.controller;

import com.foodflow.ApiIntegrationTestBase;
import com.foodflow.entity.Address;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.repository.AddressRepository;
import com.foodflow.repository.CartRepository;
import com.foodflow.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderApiIntegrationTest extends ApiIntegrationTestBase {

    @Autowired private AddressRepository addressRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private EntityManager entityManager;

    private User customer;
    private User otherCustomer;
    private User owner;
    private User otherOwner;
    private User admin;
    private Restaurant restaurant;
    private MenuItem biryani;   // 250.00
    private MenuItem raita;     //  40.00
    private Address home;

    @BeforeEach
    void setUp() {
        customer = createUser("customer@example.com", Role.CUSTOMER);
        otherCustomer = createUser("other@example.com", Role.CUSTOMER);
        owner = createUser("owner@example.com", Role.RESTAURANT_OWNER);
        otherOwner = createUser("other-owner@example.com", Role.RESTAURANT_OWNER);
        admin = createUser("admin@example.com", Role.ADMIN);
        restaurant = createRestaurant(owner, "Biryani Blues", "4.5", true);
        biryani = createMenuItem(restaurant, "Biryani", "Chicken Biryani", "250.00", true);
        raita = createMenuItem(restaurant, "Starters", "Raita", "40.00", true);
        home = addressRepository.save(new Address(customer, "Home", "12 MG Road", null, "Bengaluru",
                "Karnataka", "560001", true));
    }

    // ---------- helpers ----------

    private void addToCart(User user, MenuItem item, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items").header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":%d,\"quantity\":%d}".formatted(item.getId(), quantity)))
                .andExpect(status().isOk());
    }

    private ResultActions placeOrder(User user, Long addressId) throws Exception {
        return mockMvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":" + addressId + ",\"paymentMethod\":\"CASH_ON_DELIVERY\"}"));
    }

    private long placeStandardOrder() throws Exception {
        addToCart(customer, biryani, 2);
        addToCart(customer, raita, 1);
        return body(placeOrder(customer, home.getId()).andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions setStatus(User user, long orderId, String status) throws Exception {
        return mockMvc.perform(put("/api/orders/" + orderId + "/status").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private ResultActions getOrder(User user, long orderId) throws Exception {
        return mockMvc.perform(get("/api/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    // ---------- successful order ----------

    @Test
    void successfulOrderSnapshotsPricesComputesTotalAndClearsCart() throws Exception {
        addToCart(customer, biryani, 2);
        addToCart(customer, raita, 1);

        ResultActions result = placeOrder(customer, home.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.restaurantName").value("Biryani Blues"))
                .andExpect(jsonPath("$.deliveryAddress").value("Home: 12 MG Road, Bengaluru, Karnataka 560001"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].name").value("Chicken Biryani"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(250.00))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(500.00))
                .andExpect(jsonPath("$.subtotal").value(540.00))
                .andExpect(jsonPath("$.discountAmount").value(0))
                .andExpect(jsonPath("$.totalAmount").value(540.00))
                .andExpect(jsonPath("$.statusHistory[*].status").value(contains("PLACED")))
                .andExpect(jsonPath("$.allowedTransitions").value(contains("CANCELLED")));

        long orderId = body(result).get("id").asLong();
        result.andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/orders/" + orderId)));

        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void priceChangedAfterOrderingDoesNotChangeTheOrder() throws Exception {
        long orderId = placeStandardOrder();

        biryani.setPrice(new BigDecimal("300.00")); // owner raises the price tomorrow
        entityManager.flush();
        entityManager.clear();

        getOrder(customer, orderId)
                .andExpect(jsonPath("$.items[0].unitPrice").value(250.00))
                .andExpect(jsonPath("$.totalAmount").value(540.00));
    }

    @Test
    void deletedDishStillAppearsInOldOrders() throws Exception {
        long orderId = placeStandardOrder();
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(delete("/api/menu-items/" + biryani.getId()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        getOrder(customer, orderId)
                .andExpect(jsonPath("$.items[0].name").value("Chicken Biryani"))
                .andExpect(jsonPath("$.items[0].menuItemId").doesNotExist())
                .andExpect(jsonPath("$.items[0].unitPrice").value(250.00));
    }

    @Test
    void doubleSubmitCreatesOnlyOneOrder() throws Exception {
        placeStandardOrder();

        placeOrder(customer, home.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your cart is empty"));
        assertThat(orderRepository.findAll()).hasSize(1);
    }

    // ---------- invalid orders ----------

    @Test
    void invalidMenuItemRejectsWholeOrderAndKeepsCart() throws Exception {
        addToCart(customer, biryani, 1);
        addToCart(customer, raita, 1);
        raita.setAvailable(false); // becomes unavailable between "add to cart" and "checkout"
        entityManager.flush();

        placeOrder(customer, home.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ITEM_UNAVAILABLE"))
                .andExpect(jsonPath("$.message", containsString("Raita")));

        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(cartRepository.findWithItemsByUserId(customer.getId()).orElseThrow().getItems()).hasSize(2);
    }

    @Test
    void emptyCartCannotBeOrdered() throws Exception {
        placeOrder(customer, home.getId()).andExpect(status().isBadRequest());
    }

    @Test
    void closedRestaurantCannotReceiveOrders() throws Exception {
        addToCart(customer, biryani, 1);
        restaurant.setOpen(false);
        entityManager.flush();

        placeOrder(customer, home.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("RESTAURANT_CLOSED"));
    }

    @Test
    void cannotDeliverToSomeoneElsesAddress() throws Exception {
        Address foreign = addressRepository.save(new Address(otherCustomer, "Home", "9 Other St", null,
                "Pune", "Maharashtra", "411001", true));
        addToCart(customer, biryani, 1);

        placeOrder(customer, foreign.getId()).andExpect(status().isNotFound());
    }

    @Test
    void missingAddressIsAValidationError() throws Exception {
        mockMvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.addressId").value("addressId is required"));
    }

    @Test
    void onlyCustomersCanPlaceOrders() throws Exception {
        placeOrder(owner, home.getId()).andExpect(status().isForbidden());
    }

    // ---------- state machine through the API ----------

    @Test
    void ownerMovesOrderThroughTheWholeLifecycle() throws Exception {
        long orderId = placeStandardOrder();

        for (String next : new String[]{"CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "OUT_FOR_DELIVERY", "DELIVERED"}) {
            setStatus(owner, orderId, next).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(next));
        }

        getOrder(customer, orderId)
                .andExpect(jsonPath("$.statusHistory[*].status").value(contains(
                        "PLACED", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "OUT_FOR_DELIVERY", "DELIVERED")))
                .andExpect(jsonPath("$.allowedTransitions", hasSize(0)));
    }

    @Test
    void wrongStatusTransitionIsRejected() throws Exception {
        long orderId = placeStandardOrder();

        // Skipping steps
        setStatus(owner, orderId, "DELIVERED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value(
                        "Cannot change order status from PLACED to DELIVERED. Allowed next: [CONFIRMED, CANCELLED]"));

        // Going backwards after delivery
        for (String next : new String[]{"CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "OUT_FOR_DELIVERY", "DELIVERED"}) {
            setStatus(owner, orderId, next).andExpect(status().isOk());
        }
        setStatus(owner, orderId, "PREPARING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Cannot change order status from DELIVERED to PREPARING. Allowed next: []"));

        getOrder(owner, orderId).andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void unknownStatusValueIs400() throws Exception {
        long orderId = placeStandardOrder();

        setStatus(owner, orderId, "TELEPORTED").andExpect(status().isBadRequest());
    }

    // ---------- cancellation ----------

    @Test
    void customerCancelsPlacedOrder() throws Exception {
        long orderId = placeStandardOrder();

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Ordered by mistake\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Ordered by mistake"));
    }

    @Test
    void customerCannotCancelAfterRestaurantConfirmed() throws Exception {
        long orderId = placeStandardOrder();
        setStatus(owner, orderId, "CONFIRMED");

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ORDER_NOT_CANCELLABLE"));
    }

    @Test
    void ownerCanRejectAPlacedOrder() throws Exception {
        long orderId = placeStandardOrder();

        setStatus(owner, orderId, "CANCELLED").andExpect(status().isOk());
    }

    // ---------- who can see and change which order ----------

    @Test
    void customerCannotChangeStatusDirectly() throws Exception {
        long orderId = placeStandardOrder();

        setStatus(customer, orderId, "DELIVERED").andExpect(status().isForbidden());
    }

    @Test
    void ordersArePrivate() throws Exception {
        long orderId = placeStandardOrder();

        getOrder(customer, orderId).andExpect(status().isOk());
        getOrder(owner, orderId).andExpect(status().isOk());
        getOrder(admin, orderId).andExpect(status().isOk());
        // 404, not 403: outsiders can't even confirm the order exists.
        getOrder(otherCustomer, orderId).andExpect(status().isNotFound());
        getOrder(otherOwner, orderId).andExpect(status().isNotFound());
        setStatus(otherOwner, orderId, "CONFIRMED").andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(otherCustomer)))
                .andExpect(status().isNotFound());
    }

    // ---------- history lists ----------

    @Test
    void myOrdersListsOnlyOwnOrdersNewestFirst() throws Exception {
        long first = placeStandardOrder();
        long second = placeStandardOrder();
        addToCart(otherCustomer, biryani, 1);
        Address otherHome = addressRepository.save(new Address(otherCustomer, "Home", "9 St", null, "Pune",
                "Maharashtra", "411001", true));
        placeOrder(otherCustomer, otherHome.getId()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(first))
                .andExpect(jsonPath("$.content[0].itemCount").value(3))
                .andExpect(jsonPath("$.content[0].restaurantName").value("Biryani Blues"));
    }

    @Test
    void ownerSeesIncomingOrdersFilteredByStatus() throws Exception {
        long confirmed = placeStandardOrder();
        placeStandardOrder();
        setStatus(owner, confirmed, "CONFIRMED");

        mockMvc.perform(get("/api/owner/orders?status=PLACED").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/owner/orders").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].customerName").value("Test CUSTOMER"));
        mockMvc.perform(get("/api/owner/orders").header(HttpHeaders.AUTHORIZATION, bearer(otherOwner)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void restaurantWithOrdersCannotBeDeleted() throws Exception {
        placeStandardOrder();

        mockMvc.perform(delete("/api/restaurants/" + restaurant.getId()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("RESTAURANT_HAS_ORDERS"));
    }
}
