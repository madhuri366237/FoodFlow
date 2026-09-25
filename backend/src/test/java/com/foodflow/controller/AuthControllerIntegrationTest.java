package com.foodflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodflow.TestcontainersConfiguration;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through the real stack: HTTP request -> security filter chain (JwtFilter) ->
 * controller -> service -> repository -> PostgreSQL (Testcontainers).
 *
 * <p>{@code @Transactional} rolls each test back, so test data never leaks between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    private static final String PASSWORD = "Secret123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // ---------- helpers ----------

    private ResultActions register(String json) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private static String registrationJson(String email) {
        return """
                {"name":"Asha Rao","email":"%s","password":"%s","phone":"9876543210"}
                """.formatted(email, PASSWORD);
    }

    private String tokenFrom(ResultActions result) throws Exception {
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private User saveUser(String email, Role role) {
        return userRepository.save(new User("Test", email, passwordEncoder.encode(PASSWORD), null, role));
    }

    // ---------- registration ----------

    @Test
    void registerCreatesCustomerAndReturnsTokenWithoutPassword() throws Exception {
        register(registrationJson("new@example.com"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("new@example.com"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("$2a$"))));

        User stored = userRepository.findByEmail("new@example.com").orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2a$").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
    }

    @Test
    void registerWithDuplicateEmailReturns409() throws Exception {
        register(registrationJson("dup@example.com")).andExpect(status().isCreated());

        register(registrationJson("DUP@example.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("An account with this email already exists"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void registerWithInvalidFieldsReturns400WithFieldErrors() throws Exception {
        register("""
                {"name":"","email":"not-an-email","password":"short","phone":"12"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
    }

    // ---------- role escalation ----------

    @Test
    void roleFieldInRequestIsIgnoredSoNobodyCanRegisterAsAdmin() throws Exception {
        register("""
                {"name":"Mallory","email":"mallory@example.com","password":"Secret123","role":"ADMIN"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));

        assertThat(userRepository.findByEmail("mallory@example.com").orElseThrow().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void adminAccountTypeIsRejected() throws Exception {
        register("""
                {"name":"Mallory","email":"mallory2@example.com","password":"Secret123","accountType":"ADMIN"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("CUSTOMER, RESTAURANT_OWNER")));

        assertThat(userRepository.existsByEmail("mallory2@example.com")).isFalse();
    }

    @Test
    void restaurantOwnerCanSelfRegister() throws Exception {
        register("""
                {"name":"Owner","email":"owner@example.com","password":"Secret123","accountType":"RESTAURANT_OWNER"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("RESTAURANT_OWNER"));
    }

    // ---------- login ----------

    @Test
    void validLoginReturnsTokenThatAuthenticatesLaterRequests() throws Exception {
        saveUser("login@example.com", Role.CUSTOMER);

        String token = tokenFrom(login("LOGIN@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("login@example.com"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600)));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"));
    }

    @Test
    void invalidPasswordReturns401() throws Exception {
        saveUser("victim@example.com", Role.CUSTOMER);

        login("victim@example.com", "WrongPass1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void unknownEmailGetsExactlyTheSameResponseAsWrongPassword() throws Exception {
        login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void bruteForceIsStoppedAfterFiveFailures() throws Exception {
        saveUser("target@example.com", Role.CUSTOMER);
        for (int i = 0; i < 5; i++) {
            login("target@example.com", "Guess" + i + "abc").andExpect(status().isUnauthorized());
        }

        // Locked: even the CORRECT password now gets 429, so the attacker can't tell it was right.
        login("target@example.com", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("TOO_MANY_ATTEMPTS"));

        // The real user on another IP is unaffected.
        mockMvc.perform(post("/api/auth/login").with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"target@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        User user = saveUser("disabled@example.com", Role.CUSTOMER);
        user.setEnabled(false);

        login("disabled@example.com", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
    }

    @Test
    void existingTokenStopsWorkingOnceAccountIsDisabled() throws Exception {
        User user = saveUser("later-disabled@example.com", Role.CUSTOMER);
        String token = tokenFrom(login("later-disabled@example.com", PASSWORD));

        user.setEnabled(false);
        userRepository.flush();

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---------- JWT filter and role-based authorization ----------

    @Test
    void protectedEndpointWithoutTokenReturns401Json() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void tamperedOrGarbageTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotAccessAdminApi() throws Exception {
        saveUser("customer@example.com", Role.CUSTOMER);
        String token = tokenFrom(login("customer@example.com", PASSWORD));

        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void ownerCannotAccessAdminApi() throws Exception {
        saveUser("owner2@example.com", Role.RESTAURANT_OWNER);
        String token = tokenFrom(login("owner2@example.com", PASSWORD));

        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPassesTheAdminRoleCheck() throws Exception {
        saveUser("admin@example.com", Role.ADMIN);
        String token = tokenFrom(login("admin@example.com", PASSWORD));

        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }
}
