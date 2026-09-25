package com.foodflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Anything time-dependent (token expiry now; coupon expiry and "today's orders" later)
 * asks this Clock for the current time instead of calling Instant.now() directly.
 * Tests can then pass a fixed or shifted Clock and check expiry deterministically.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
