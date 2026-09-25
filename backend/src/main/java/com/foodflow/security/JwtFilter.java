package com.foodflow.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request, before Spring Security's authorization checks.
 *
 * <pre>
 * Authorization: Bearer &lt;token&gt;
 *   -> verify signature + expiry (JwtUtil)
 *   -> load the CURRENT user from the database by id
 *   -> if enabled, put an Authentication into the SecurityContext
 * </pre>
 *
 * <p>If the header is missing or the token is invalid, the filter does nothing and the
 * request continues as anonymous. Public endpoints still work; protected ones are then
 * rejected with 401 by the AuthenticationEntryPoint. The filter never writes responses itself.
 *
 * <p>Not a {@code @Component} on purpose: Spring Boot registers every Filter bean as a
 * servlet filter, so it would run a second time outside the security chain.
 * SecurityConfig creates it with {@code new}.
 */
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    public JwtFilter(JwtUtil jwtUtil, CustomUserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            authenticate(header.substring(BEARER_PREFIX.length()).trim(), request);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            Long userId = jwtUtil.extractUserId(token);

            /*
             * Reload the user instead of trusting the role claim inside the token.
             * Cost: one primary-key lookup per request.
             * Benefit: a disabled account or a changed role takes effect immediately,
             * instead of only after the (up to 24 h) token expires.
             */
            UserPrincipal principal = userDetailsService.loadUserById(userId);
            if (!principal.isEnabled()) {
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            // Invalid, expired or tampered token, or a deleted user: stay anonymous.
            log.debug("Rejected JWT: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
    }
}
