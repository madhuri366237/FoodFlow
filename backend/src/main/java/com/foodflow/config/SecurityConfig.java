package com.foodflow.config;

import com.foodflow.security.CustomUserDetailsService;
import com.foodflow.security.JwtFilter;
import com.foodflow.security.JwtUtil;
import com.foodflow.security.RestAccessDeniedHandler;
import com.foodflow.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Central security configuration: which endpoints are public, which roles may call what,
 * and how a request is authenticated (JWT, no sessions).
 *
 * <p>{@code @EnableMethodSecurity} turns on {@code @PreAuthorize("hasRole('ADMIN')")} on
 * controller/service methods; later phases use it for finer rules than URL patterns allow.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // Endpoints anyone may call without a token.
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/register",
            "/api/auth/login",
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            // Spring forwards unhandled errors here; blocking it would turn every error into a 401.
            "/error"
    };

    // Browsing is public (Phase 4): anyone may look at restaurants, menus and categories.
    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/restaurants/**",
            "/api/categories/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtUtil jwtUtil,
                                                   CustomUserDetailsService userDetailsService,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                /*
                 * CSRF protection is disabled because it defends against something that cannot
                 * happen here. CSRF abuses cookies, which the browser attaches automatically to
                 * cross-site requests. Our token travels in the Authorization header, which a
                 * malicious site cannot make the browser send.
                 */
                .csrf(AbstractHttpConfigurer::disable)
                // Uses the corsConfigurationSource bean defined below.
                .cors(Customizer.withDefaults())
                // STATELESS: never create an HttpSession. Every request must carry its own JWT.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // No login form and no Basic-auth popup; the API only speaks JSON.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint) // 401 JSON
                        .accessDeniedHandler(accessDeniedHandler))          // 403 JSON
                /*
                 * Rules are checked top to bottom; the first match wins.
                 * anyRequest().authenticated() is the safe default: a new endpoint
                 * is protected unless someone deliberately makes it public.
                 */
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // CORS preflight
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/owner/**").hasRole("RESTAURANT_OWNER")
                        .anyRequest().authenticated())
                // Run our JWT check before Spring's username/password filter.
                .addFilterBefore(new JwtFilter(jwtUtil, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /*
     * BCrypt: a deliberately slow, salted hash. The random per-password salt means two users
     * with the same password get different hashes, which defeats precomputed rainbow tables.
     * The cost factor (default 10 = 2^10 rounds, ~100 ms) makes brute-forcing a stolen hash
     * database expensive, and can be raised as hardware gets faster.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * Spring Boot builds an AuthenticationManager containing a DaoAuthenticationProvider wired
     * with our UserDetailsService and PasswordEncoder beans. We expose it so AuthService can
     * call authenticate(email, password).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /*
     * CORS: browsers block JavaScript on http://localhost:5173 from reading responses from
     * http://localhost:8080 unless the server explicitly allows that origin. Only the
     * configured origins are allowed; never "*" for an authenticated API.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        config.setExposedHeaders(List.of(HttpHeaders.LOCATION));
        // No cookies are used, so credentials (cookies) are not allowed cross-origin.
        config.setAllowCredentials(false);
        // Browsers may cache the preflight answer for an hour.
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
