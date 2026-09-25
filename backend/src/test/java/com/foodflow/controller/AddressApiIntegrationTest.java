package com.foodflow.controller;

import com.foodflow.ApiIntegrationTestBase;
import com.foodflow.dto.address.AddressRequest;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AddressApiIntegrationTest extends ApiIntegrationTestBase {

    private User customer;
    private User otherCustomer;

    @BeforeEach
    void setUp() {
        customer = createUser("customer@example.com", Role.CUSTOMER);
        otherCustomer = createUser("other@example.com", Role.CUSTOMER);
    }

    private ResultActions create(User user, String label, Boolean makeDefault) throws Exception {
        AddressRequest request = new AddressRequest(label, "12 MG Road", null, "Bengaluru", "Karnataka", "560001",
                makeDefault);
        return mockMvc.perform(post("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(request)));
    }

    @Test
    void firstAddressBecomesDefaultAutomatically() throws Exception {
        create(customer, "Home", null).andExpect(status().isCreated()).andExpect(jsonPath("$.isDefault").value(true));
        create(customer, "Work", null).andExpect(jsonPath("$.isDefault").value(false));
    }

    @Test
    void makingAnotherAddressDefaultMovesTheFlag() throws Exception {
        create(customer, "Home", null);
        long work = body(create(customer, "Work", null)).get("id").asLong();

        mockMvc.perform(patch("/api/addresses/" + work + "/default").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));

        mockMvc.perform(get("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].label").value(contains("Work", "Home")))
                .andExpect(jsonPath("$[*].isDefault").value(contains(true, false)));
    }

    @Test
    void invalidPinCodeIsRejected() throws Exception {
        AddressRequest bad = new AddressRequest("Home", "12 MG Road", null, "Bengaluru", "Karnataka", "012345", null);
        mockMvc.perform(post("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(json(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.postalCode").exists());
    }

    @Test
    void addressesArePrivate() throws Exception {
        long id = body(create(customer, "Home", null)).get("id").asLong();

        mockMvc.perform(delete("/api/addresses/" + id).header(HttpHeaders.AUTHORIZATION, bearer(otherCustomer)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/addresses").header(HttpHeaders.AUTHORIZATION, bearer(otherCustomer)))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(delete("/api/addresses/" + id).header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isNoContent());
    }
}
