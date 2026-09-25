package com.foodflow.repository;

import com.foodflow.TestcontainersConfiguration;
import com.foodflow.entity.Category;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

/**
 * Base for repository (persistence-layer) tests.
 *
 * <ul>
 *   <li>{@code @DataJpaTest} starts only JPA, Flyway and repositories (no web layer),
 *       and wraps every test in a transaction that is rolled back afterwards,
 *       so tests never see each other's data.</li>
 *   <li>{@code replace = NONE} stops Spring from swapping in an embedded H2 database;
 *       we want the real PostgreSQL container.</li>
 * </ul>
 *
 * <p>Constraint tests call {@code saveAndFlush}/{@code flush}: without a flush, Hibernate
 * would only send the INSERT at commit, and the test transaction never commits.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
abstract class RepositoryTestBase {

    // Dummy BCrypt-shaped value; real hashing arrives in Phase 3.
    protected static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuuDummyHashForTestsOnly1234567890ab";

    @Autowired
    protected TestEntityManager em;

    // For raw SQL that bypasses JPA, to prove the DATABASE enforces a rule, not just Java.
    @Autowired
    protected JdbcTemplate jdbc;

    protected User persistUser(String email, Role role) {
        return em.persist(new User("Test " + role, email, PASSWORD_HASH, "9000000000", role));
    }

    protected Restaurant persistRestaurant(User owner, String name) {
        return em.persist(new Restaurant(owner, name, "Test restaurant", "1 Test Street", "9000000001", null));
    }

    protected Category persistCategory(String name) {
        return em.persist(new Category(name, null, null));
    }

    protected MenuItem persistMenuItem(Restaurant restaurant, Category category, String name, String price) {
        return em.persist(new MenuItem(restaurant, category, name, null, new BigDecimal(price), null));
    }

    /** Writes pending SQL and empties the first-level cache, so the next read really hits PostgreSQL. */
    protected void flushAndClear() {
        em.flush();
        em.clear();
    }
}
