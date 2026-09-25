package com.foodflow.config;

import com.foodflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the app with seeding ON (its own Spring context, so its own fresh PostgreSQL container).
 * Because the seeder drives the real services, this also runs register -> cart -> checkout ->
 * payment -> state machine -> review end to end.
 */
@SpringBootTest(properties = "app.seed-demo-data=true")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DemoDataSeederTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DemoDataSeeder seeder;
    @Autowired private PasswordEncoder passwordEncoder;

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    @Test
    void seedsTheRequiredDemoDataset() {
        assertThat(count("select count(*) from users where role = 'ADMIN' and email like '%@demo.foodflow.dev'")).isEqualTo(1);
        assertThat(count("select count(*) from users where role = 'RESTAURANT_OWNER'")).isEqualTo(2);
        assertThat(count("select count(*) from users where role = 'CUSTOMER'")).isEqualTo(5);
        assertThat(count("select count(*) from restaurants")).isEqualTo(5);
        assertThat(count("select count(*) from menu_items")).isGreaterThanOrEqualTo(20);
        assertThat(count("select count(*) from coupons")).isEqualTo(3);
        assertThat(count("select count(*) from orders")).isEqualTo(11);
        assertThat(count("select count(*) from reviews")).isEqualTo(6);
    }

    @Test
    void seededDataFollowsTheBusinessRules() {
        // Biryani House was reviewed 5 and 4 -> 4.5 from the real rating calculation.
        assertThat(jdbc.queryForObject("select rating from restaurants where name = 'Biryani House'", BigDecimal.class))
                .isEqualByComparingTo("4.5");
        // WELCOME50 on 2 x 279 + 199 = 757: 50% capped at 100.
        assertThat(jdbc.queryForObject("select discount_amount from orders where coupon_code = 'WELCOME50'", BigDecimal.class))
                .isEqualByComparingTo("100.00");
        // Cancelled paid order: refunded after commit, and its coupon use given back.
        assertThat(count("select count(*) from payments where status = 'REFUNDED'")).isEqualTo(1);
        assertThat(count("select used_count from coupons where code = 'SAVE10'")).isZero();
        // Orders are spread over the last week for the dashboard charts.
        assertThat(count("select count(distinct (created_at at time zone 'Asia/Kolkata')::date) from orders")).isGreaterThan(3);
    }

    @Test
    void demoPasswordIsHashedAndWorks() {
        String hash = jdbc.queryForObject("select password_hash from users where email = 'customer1@demo.foodflow.dev'", String.class);

        assertThat(hash).startsWith("$2a$");
        assertThat(passwordEncoder.matches(DemoDataSeeder.DEMO_PASSWORD, hash)).isTrue();
    }

    @Test
    void runningAgainDoesNotDuplicateAnything() throws Exception {
        long orders = count("select count(*) from orders");

        seeder.run(new DefaultApplicationArguments());

        assertThat(count("select count(*) from orders")).isEqualTo(orders);
        assertThat(count("select count(*) from users")).isEqualTo(8);
    }
}
