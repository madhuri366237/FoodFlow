package com.foodflow.controller;

import com.foodflow.ApiIntegrationTestBase;
import com.foodflow.entity.Address;
import com.foodflow.entity.Coupon;
import com.foodflow.entity.DiscountType;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.repository.AddressRepository;
import com.foodflow.repository.CartRepository;
import com.foodflow.repository.CouponRepository;
import com.foodflow.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CouponApiIntegrationTest extends ApiIntegrationTestBase {

    @Autowired private CouponRepository couponRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private EntityManager entityManager;

    private User customer;
    private User admin;
    private MenuItem biryani; // 250.00
    private Address home;

    @BeforeEach
    void setUp() {
        customer = createUser("customer@example.com", Role.CUSTOMER);
        admin = createUser("admin@example.com", Role.ADMIN);
        User owner = createUser("owner@example.com", Role.RESTAURANT_OWNER);
        Restaurant restaurant = createRestaurant(owner, "Biryani Blues", "4.5", true);
        biryani = createMenuItem(restaurant, "Biryani", "Chicken Biryani", "250.00", true);
        home = addressRepository.save(new Address(customer, "Home", "12 MG Road", null, "Bengaluru",
                "Karnataka", "560001", true));
    }

    // ---------- helpers ----------

    private Coupon coupon(String code, DiscountType type, String value) {
        return couponRepository.save(new Coupon(code, type, new BigDecimal(value), Instant.now().plus(Duration.ofDays(30))));
    }

    private void cartWith(int biryanis) throws Exception {
        mockMvc.perform(post("/api/cart/items").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":%d,\"quantity\":%d}".formatted(biryani.getId(), biryanis)))
                .andExpect(status().isOk());
    }

    private ResultActions validate(String code) throws Exception {
        return mockMvc.perform(post("/api/coupons/validate").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions checkout(String couponCode) throws Exception {
        return mockMvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":%d,\"paymentMethod\":\"CASH_ON_DELIVERY\",\"couponCode\":\"%s\"}"
                        .formatted(home.getId(), couponCode)));
    }

    private int usedCount(Coupon coupon) {
        entityManager.flush();
        entityManager.clear(); // the atomic UPDATE bypassed the in-memory entity; re-read it
        return couponRepository.findById(coupon.getId()).orElseThrow().getUsedCount();
    }

    // ---------- validate (cart preview) ----------

    @Test
    void validCouponPreviewUsesTheServerPricedCart() throws Exception {
        coupon("SAVE10", DiscountType.PERCENTAGE, "10");
        cartWith(2); // 500.00

        validate("save10") // case-insensitive
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SAVE10"))
                .andExpect(jsonPath("$.subtotal").value(500.00))
                .andExpect(jsonPath("$.discount").value(50.00))
                .andExpect(jsonPath("$.total").value(450.00));
    }

    @Test
    void previewDoesNotConsumeAUse() throws Exception {
        Coupon saved = coupon("ONCE", DiscountType.FIXED_AMOUNT, "50");
        saved.setUsageLimit(1);
        cartWith(1);

        validate("ONCE").andExpect(status().isOk());
        validate("ONCE").andExpect(status().isOk());

        assertThat(usedCount(saved)).isZero();
    }

    @Test
    void invalidCoupon() throws Exception {
        cartWith(1);

        validate("NOSUCHCODE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_COUPON"));
    }

    @Test
    void expiredCoupon() throws Exception {
        Coupon expired = coupon("OLD10", DiscountType.PERCENTAGE, "10");
        expired.setExpiresAt(Instant.now().minus(Duration.ofDays(1)));
        cartWith(1);

        validate("OLD10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("COUPON_EXPIRED"));
    }

    @Test
    void maxDiscountCapIsApplied() throws Exception {
        Coupon half = coupon("HALF", DiscountType.PERCENTAGE, "50");
        half.setMaximumDiscount(new BigDecimal("100.00"));
        cartWith(4); // 1000.00 -> 50% would be 500

        validate("HALF")
                .andExpect(jsonPath("$.discount").value(100.00))
                .andExpect(jsonPath("$.total").value(900.00));
    }

    @Test
    void minimumOrderNotMet() throws Exception {
        Coupon big = coupon("BIG", DiscountType.FIXED_AMOUNT, "100");
        big.setMinimumOrderAmount(new BigDecimal("600.00"));
        cartWith(2); // 500.00

        validate("BIG")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("COUPON_MINIMUM_NOT_MET"))
                .andExpect(jsonPath("$.message").value(
                        "Coupon BIG needs a minimum order of 600.00 (your order: 500.00). Add 100.00 more."));
    }

    @Test
    void emptyCartCannotBeValidated() throws Exception {
        coupon("SAVE10", DiscountType.PERCENTAGE, "10");

        validate("SAVE10").andExpect(status().isBadRequest());
    }

    // ---------- checkout with a coupon ----------

    @Test
    void checkoutAppliesDiscountAndTakesOneUse() throws Exception {
        Coupon saved = coupon("SAVE10", DiscountType.PERCENTAGE, "10");
        cartWith(2);

        checkout("save10")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(500.00))
                .andExpect(jsonPath("$.discountAmount").value(50.00))
                .andExpect(jsonPath("$.couponCode").value("SAVE10"))
                .andExpect(jsonPath("$.totalAmount").value(450.00));

        assertThat(usedCount(saved)).isEqualTo(1);
    }

    @Test
    void onlinePaymentChargesTheDiscountedTotal() throws Exception {
        coupon("FLAT100", DiscountType.FIXED_AMOUNT, "100");
        cartWith(2);
        long orderId = body(mockMvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":%d,\"paymentMethod\":\"CARD\",\"couponCode\":\"FLAT100\"}".formatted(home.getId()))))
                .get("id").asLong();

        mockMvc.perform(post("/api/payments").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d,\"paymentToken\":\"tok_visa\"}".formatted(orderId)))
                .andExpect(jsonPath("$.amount").value(400.00));
    }

    @Test
    void exhaustedCouponFailsTheWholeCheckout() throws Exception {
        Coupon saved = coupon("GONE", DiscountType.FIXED_AMOUNT, "50");
        saved.setUsageLimit(1);
        couponRepository.saveAndFlush(saved);
        entityManager.createNativeQuery("update coupons set used_count = 1 where id = " + saved.getId()).executeUpdate();
        entityManager.clear();
        cartWith(1);

        checkout("GONE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("COUPON_USAGE_LIMIT_REACHED"));

        // No order, cart intact: the customer can retry without the coupon.
        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(cartRepository.findWithItemsByUserId(customer.getId()).orElseThrow().getItems()).hasSize(1);
    }

    @Test
    void cancellingAnOrderReleasesItsCouponUse() throws Exception {
        Coupon saved = coupon("SAVE10", DiscountType.PERCENTAGE, "10");
        cartWith(1);
        long orderId = body(checkout("SAVE10")).get("id").asLong();
        assertThat(usedCount(saved)).isEqualTo(1);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk());

        assertThat(usedCount(saved)).isZero();
    }

    // ---------- listing and admin ----------

    @Test
    void availableListHidesExpiredAndInactiveCoupons() throws Exception {
        coupon("LIVE", DiscountType.PERCENTAGE, "10");
        coupon("OFF", DiscountType.PERCENTAGE, "10").setActive(false);
        coupon("PAST", DiscountType.PERCENTAGE, "10").setExpiresAt(Instant.now().minus(Duration.ofDays(1)));
        entityManager.flush();

        mockMvc.perform(get("/api/coupons").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(jsonPath("$[*].code").value(hasItem("LIVE")))
                .andExpect(jsonPath("$[*].code").value(not(hasItem("OFF"))))
                .andExpect(jsonPath("$[*].code").value(not(hasItem("PAST"))));
    }

    private ResultActions adminCreate(User user, String json) throws Exception {
        return mockMvc.perform(post("/api/admin/coupons").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String couponJson(String code, String type, String value) {
        return """
                {"code":"%s","discountType":"%s","discountValue":%s,"maximumDiscount":150,
                 "usageLimit":100,"expiresAt":"%s"}
                """.formatted(code, type, value, Instant.now().plus(Duration.ofDays(30)));
    }

    @Test
    void adminCreatesCoupon() throws Exception {
        adminCreate(admin, couponJson("welcome50", "PERCENTAGE", "50"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("WELCOME50"))
                .andExpect(jsonPath("$.usedCount").value(0))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void invalidCouponDefinitionsAreRejected() throws Exception {
        adminCreate(admin, couponJson("TOOMUCH", "PERCENTAGE", "150"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A percentage discount cannot exceed 100"));
        adminCreate(admin, couponJson("NEG", "FIXED_AMOUNT", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.discountValue").exists());
        adminCreate(admin, couponJson("x!", "FIXED_AMOUNT", "5"))
                .andExpect(jsonPath("$.fieldErrors.code").exists());
    }

    @Test
    void duplicateCodeIsRejected() throws Exception {
        coupon("SAVE10", DiscountType.PERCENTAGE, "10");

        adminCreate(admin, couponJson("save10", "PERCENTAGE", "10")).andExpect(status().isConflict());
    }

    @Test
    void usageLimitCannotDropBelowUsesAlreadyMade() throws Exception {
        Coupon saved = coupon("SAVE10", DiscountType.PERCENTAGE, "10");
        cartWith(1);
        checkout("SAVE10");
        checkout("SAVE10"); // cart now empty -> 400, only one use taken
        cartWith(1);
        checkout("SAVE10");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(put("/api/admin/coupons/" + saved.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson("SAVE10", "PERCENTAGE", "10").replace("\"usageLimit\":100", "\"usageLimit\":1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("usageLimit cannot be lower than the 2 uses already made"));
    }

    @Test
    void onlyAdminsManageCoupons() throws Exception {
        adminCreate(customer, couponJson("HACK", "PERCENTAGE", "100")).andExpect(status().isForbidden());
    }
}
