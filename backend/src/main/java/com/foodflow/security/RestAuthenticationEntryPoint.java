package com.foodflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodflow.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 Unauthorized: "we don't know who you are". Called by Spring Security when an
 * anonymous request (no token, or an invalid/expired one) hits a protected endpoint.
 * Replaces Spring's default (an HTML page or a Basic-auth browser popup) with our JSON format.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(401, "UNAUTHORIZED",
                "Authentication is required. Send a valid token as 'Authorization: Bearer <token>'",
                request.getRequestURI()));
    }
}
