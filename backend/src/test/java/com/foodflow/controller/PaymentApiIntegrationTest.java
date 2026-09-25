package com.foodflow.controller;

import com.foodflow.ApiIntegrationTestBase;
import com.foodflow.entity.Address;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.repository.AddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentApiIntegrationTest extends ApiIntegrationTestBase {

    @Autowired private AddressRepository addressRepository;
    @Autowired private JdbcTemplate jdbc;

    private User customer;
    private User otherCustomer;
    private User owner;
    private MenuItem biryani; // 250.00
    private Address home;

    @BeforeEach
    void setUp() {
        customer = createUser("customer@example.com", Role.CUSTOMER);
        otherCustomer = createUser("other@example.com", Role.CUSTOMER);
        owner = createUser("owner@example.com", Role.RESTAURANT_OWNER);
        Restaurant restaurant = createRestaurant(owner, "Biryani Blues", "4.5", true);
        biryani = createMenuItem(restaurant, "Biryani", "Chicken Biryani", "250.00", true);
        home = addressRepository.save(new Address(customer, "Home", "12 MG Road", null, "Bengaluru",
                "Karnataka", "560001", true));
    }

    // ---------- helpers ----------

    private long placeOrder(String paymentMethod) throws Exception {
        mockMvc.perform(post("/api/cart/items").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuItemId\":%d,\"quantity\":2}".formatted(biryani.getId())));
        return body(mockMvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":%d,\"paymentMethod\":\"%s\"}".formatted(home.getId(), paymentMethod)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentMethod").value(paymentMethod))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))).get("id").asLong();
    }

    private ResultActions pay(User user, long orderId, String token) throws Exception {
        return mockMvc.perform(post("/api/payments").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":%d,\"paymentToken\":\"%s\"}".formatted(orderId, token)));
    }

    private ResultActions setStatus(long orderId, String status) throws Exception {
        return mockMvc.perform(put("/api/orders/" + orderId + "/status").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private ResultActions getOrder(User user, long orderId) throws Exception {
        return mockMvc.perform(get("/api/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    // ---------- online payment ----------

    @Test
    void successfulCardPaymentMarksOrderPaidAndUnlocksConfirmation() throws Exception {
        long orderId = placeOrder("CARD");

        // Unpaid online order: the restaurant may not start cooking yet.
        getOrder(owner, orderId).andExpect(jsonPath("$.allowedTransitions").value(not(hasItem("CONFIRMED"))));
        setStatus(orderId, "CONFIRMED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PAYMENT_PENDING"));

        pay(customer, orderId, "tok_visa")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(500.00))   // the ORDER's total, computed server-side
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.provider").value("SIMULATED"))
                .andExpect(jsonPath("$.transactionReference", startsWith("SIM_TXN_")));

        getOrder(customer, orderId).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        setStatus(orderId, "CONFIRMED").andExpect(status().isOk());
    }

    @Test
    void declinedPaymentIsRecordedAndCustomerCanRetry() throws Exception {
        long orderId = placeOrder("UPI");

        // A decline is a valid outcome, not an HTTP error: 201 with status FAILED.
        pay(customer, orderId, "tok_chargeDeclined")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Card declined"));
        getOrder(customer, orderId).andExpect(jsonPath("$.paymentStatus").value("PENDING"));

        pay(customer, orderId, "upi_success").andExpect(jsonPath("$.status").value("SUCCESS"));

        mockMvc.perform(get("/api/orders/" + orderId + "/payments").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].status").value(contains("FAILED", "SUCCESS")));
    }

    @Test
    void gatewayOutageBecomesAFailedPaymentNotA500() throws Exception {
        long orderId = placeOrder("CARD");

        pay(customer, orderId, "tok_timeout")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Payment provider unavailable. Please try again."));
    }

    @Test
    void paidOrderCannotBePaidAgain() throws Exception {
        long orderId = placeOrder("CARD");
        pay(customer, orderId, "tok_visa");

        pay(customer, orderId, "tok_visa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_PAID"));
    }

    @Test
    void databaseAllowsOnlyOneActivePaymentPerOrder() throws Exception {
        long orderId = placeOrder("CARD");
        jdbc.update("insert into payments (order_id, amount, method, status, provider) values (?, 500, 'CARD', 'PENDING', 'SIMULATED')", orderId);

        // The race two simultaneous "Pay" clicks would create: a second PENDING row.
        assertThatThrownBy(() -> jdbc.update(
                "insert into payments (order_id, amount, method, status, provider) values (?, 500, 'CARD', 'PENDING', 'SIMULATED')", orderId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_payments_one_active_per_order");
    }

    @Test
    void paymentInProgressBlocksASecondAttempt() throws Exception {
        long orderId = placeOrder("CARD");
        jdbc.update("insert into payments (order_id, amount, method, status, provider) values (?, 500, 'CARD', 'PENDING', 'SIMULATED')", orderId);

        pay(customer, orderId, "tok_visa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PAYMENT_IN_PROGRESS"));
    }

    @Test
    void cashOnDeliveryOrdersAreNotPaidOnline() throws Exception {
        long orderId = placeOrder("CASH_ON_DELIVERY");

        pay(customer, orderId, "tok_visa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PAYMENT_NOT_REQUIRED"));
    }

    @Test
    void cancelledOrderCannotBePaid() throws Exception {
        long orderId = placeOrder("CARD");
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(customer)));

        pay(customer, orderId, "tok_visa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ORDER_NOT_PAYABLE"));
    }

    @Test
    void cannotPaySomeoneElsesOrder() throws Exception {
        long orderId = placeOrder("CARD");

        pay(otherCustomer, orderId, "tok_visa").andExpect(status().isNotFound());
    }

    @Test
    void invalidPaymentRequestIs400() throws Exception {
        mockMvc.perform(post("/api/payments").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.paymentToken").value("paymentToken is required"));
    }

    // ---------- cash on delivery ----------

    @Test
    void cashOnDeliveryIsCollectedWhenDelivered() throws Exception {
        long orderId = placeOrder("CASH_ON_DELIVERY");
        setStatus(orderId, "CONFIRMED").andExpect(status().isOk()); // no online payment needed

        for (String next : new String[]{"PREPARING", "READY_FOR_PICKUP", "OUT_FOR_DELIVERY", "DELIVERED"}) {
            setStatus(orderId, next).andExpect(status().isOk());
        }

        getOrder(customer, orderId).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        mockMvc.perform(get("/api/orders/" + orderId + "/payments").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].transactionReference").value("COD-" + orderId));
    }

    @Test
    void cancelledCashOrderCancelsItsExpectedPayment() throws Exception {
        long orderId = placeOrder("CASH_ON_DELIVERY");
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/" + orderId + "/payments").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    // ---------- visibility ----------

    @Test
    void paymentsFollowOrderVisibility() throws Exception {
        long orderId = placeOrder("CARD");
        long paymentId = body(pay(customer, orderId, "tok_visa")).get("id").asLong();

        mockMvc.perform(get("/api/payments/" + paymentId).header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/payments/" + paymentId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/payments/" + paymentId).header(HttpHeaders.AUTHORIZATION, bearer(otherCustomer)))
                .andExpect(status().isNotFound());
    }
}
