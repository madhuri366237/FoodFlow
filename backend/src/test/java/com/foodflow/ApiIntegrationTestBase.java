package com.foodflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodflow.entity.Category;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import com.foodflow.repository.CategoryRepository;
import com.foodflow.repository.MenuItemRepository;
import com.foodflow.repository.RestaurantRepository;
import com.foodflow.repository.UserRepository;
import com.foodflow.security.JwtUtil;
import com.foodflow.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Base for API integration tests: real HTTP layer (MockMvc), real security filter chain,
 * real PostgreSQL (Testcontainers). Each test runs in a transaction that is rolled back,
 * so tests are independent. All subclasses share one Spring context and one container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
public abstract class ApiIntegrationTestBase {

    private static final String DUMMY_HASH = "$2a$10$abcdefghijklmnopqrstuuDummyHashForTestsOnly1234567890ab";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RestaurantRepository restaurantRepository;
    @Autowired protected MenuItemRepository menuItemRepository;
    @Autowired protected CategoryRepository categoryRepository;
    @Autowired private JwtUtil jwtUtil;

    protected User createUser(String email, Role role) {
        return userRepository.save(new User("Test " + role, email, DUMMY_HASH, null, role));
    }

    /** A real signed token, as login would return; skips the BCrypt cost of a login call. */
    protected String bearer(User user) {
        return "Bearer " + jwtUtil.generateToken(UserPrincipal.from(user));
    }

    protected Restaurant createRestaurant(User owner, String name, String rating, boolean open) {
        Restaurant restaurant = new Restaurant(owner, name, "Test restaurant", "1 Test Street", "9000000001", null);
        restaurant.updateRating(new BigDecimal(rating), 10);
        restaurant.setOpen(open);
        return restaurantRepository.save(restaurant);
    }

    protected MenuItem createMenuItem(Restaurant restaurant, String categoryName, String name,
                                      String price, boolean available) {
        MenuItem item = new MenuItem(restaurant, category(categoryName), name, null, new BigDecimal(price), null);
        item.setAvailable(available);
        return menuItemRepository.save(item);
    }

    /** Reference categories come from the V3 migration. */
    protected Category category(String name) {
        return categoryRepository.findByNameIgnoreCase(name).orElseThrow();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
