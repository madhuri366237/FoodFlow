package com.foodflow.controller;

import com.foodflow.config.CorsProperties;
import com.foodflow.config.SecurityConfig;
import com.foodflow.entity.OrderStatus;
import com.foodflow.entity.Role;
import com.foodflow.security.CustomUserDetailsService;
import com.foodflow.security.JwtUtil;
import com.foodflow.security.RestAccessDeniedHandler;
import com.foodflow.security.RestAuthenticationEntryPoint;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.foodflow.TestFixtures.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A "slice" test: @WebMvcTest starts ONLY the web layer (this controller, the real SecurityConfig,
 * GlobalExceptionHandler, JSON). The service is a Mockito mock, and there is no database and no
 * Docker, so it starts in about a second and tests exactly one thing: who may reach which
 * endpoint, with which body.
 */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(CorsProperties.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class OrderControllerSecurityTest {

    private static final String STATUS_BODY = "{\"status\":\"CONFIRMED\"}";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private OrderService orderService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService userDetailsService;

    private static UserPrincipal as(Role role) {
        return UserPrincipal.from(user(1L, role));
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(put("/api/orders/5/status").contentType(MediaType.APPLICATION_JSON).content(STATUS_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        verify(orderService, never()).changeStatus(any(), any(), any(), any());
    }

    @Test
    void customerCannotChangeStatus() throws Exception {
        mockMvc.perform(put("/api/orders/5/status").with(user(as(Role.CUSTOMER)))
                        .contentType(MediaType.APPLICATION_JSON).content(STATUS_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        verify(orderService, never()).changeStatus(any(), any(), any(), any());
    }

    @Test
    void ownerAndAdminReachTheService() throws Exception {
        for (Role role : new Role[]{Role.RESTAURANT_OWNER, Role.ADMIN}) {
            mockMvc.perform(put("/api/orders/5/status").with(user(as(role)))
                            .contentType(MediaType.APPLICATION_JSON).content(STATUS_BODY))
                    .andExpect(status().isOk());
        }
        verify(orderService, org.mockito.Mockito.times(2)).changeStatus(eq(5L), any(), eq(OrderStatus.CONFIRMED), any());
    }

    @Test
    void onlyCustomersPlaceOrders() throws Exception {
        String body = "{\"addressId\":1,\"paymentMethod\":\"UPI\"}";
        mockMvc.perform(post("/api/orders").with(user(as(Role.RESTAURANT_OWNER)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/orders").with(user(as(Role.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidBodyIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/orders").with(user(as(Role.CUSTOMER)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"BITCOIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CARD, UPI, CASH_ON_DELIVERY")));
        verify(orderService, never()).placeOrder(any(), any());
    }
}
