package com.foodflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the full application context starts against real PostgreSQL.
 * Starting successfully proves Flyway applied every migration AND Hibernate's
 * ddl-auto=validate found every entity/column it expects.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FoodFlowApplicationTests {

    @Test
    void contextLoads() {
    }
}
