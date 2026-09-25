package com.foodflow.controller;

import com.foodflow.ApiIntegrationTestBase;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Audit (Phase 15): client mistakes must never surface as "500 Internal Server Error".
 * Each case gets the correct 4xx status in our standard JSON error format.
 */
class ErrorHandlingAuditTest extends ApiIntegrationTestBase {

    @Test
    void wrongContentTypeIs415() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void missingBodyIs400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void unacceptableResponseTypeIs406() throws Exception {
        mockMvc.perform(get("/api/categories").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void wrongMethodIs405ForAuthenticatedCallers() throws Exception {
        // Anonymous callers get 401 first (security runs before routing), which is intended:
        // strangers shouldn't learn which methods exist. Logged-in callers get the precise 405.
        User customer = createUser("c@example.com", Role.CUSTOMER);
        mockMvc.perform(post("/api/categories").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void overlongSearchKeywordIsRejected() throws Exception {
        User admin = createUser("admin@example.com", Role.ADMIN);

        mockMvc.perform(get("/api/admin/users").param("keyword", "x".repeat(101))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.keyword").value("keyword must be at most 100 characters"));
    }
}
