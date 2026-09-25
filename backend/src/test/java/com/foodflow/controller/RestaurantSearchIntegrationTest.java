package com.foodflow.controller;

import com.foodflow.ApiIntegrationTestBase;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public search: filtering, keyword matching, sorting and pagination, all done in SQL.
 * Every request here is anonymous: browsing needs no token.
 *
 * Test data (rating, open?, active?, dishes):
 *   Biryani Blues  4.5 open    - Chicken Biryani (Biryani) 250
 *   Dosa Corner    4.2 CLOSED  - Masala Dosa (South Indian) 90
 *   100% Veg       4.0 open    - Veg Thali (North Indian) 150
 *   Pizza Point    3.8 open    - Veg Pizza (Pizza) 199, Paneer Biryani (Biryani) 180 UNAVAILABLE
 *   Hidden Cafe    5.0 open, INACTIVE - Biryani Special (Biryani) 300
 */
class RestaurantSearchIntegrationTest extends ApiIntegrationTestBase {

    @Autowired
    private EntityManager entityManager;

    private Restaurant hidden;
    private Restaurant pizzaPoint;

    @BeforeEach
    void setUp() {
        User owner = createUser("owner@example.com", Role.RESTAURANT_OWNER);

        Restaurant biryaniBlues = createRestaurant(owner, "Biryani Blues", "4.5", true);
        createMenuItem(biryaniBlues, "Biryani", "Chicken Biryani", "250.00", true);

        Restaurant dosaCorner = createRestaurant(owner, "Dosa Corner", "4.2", false);
        createMenuItem(dosaCorner, "South Indian", "Masala Dosa", "90.00", true);

        Restaurant veg = createRestaurant(owner, "100% Veg", "4.0", true);
        createMenuItem(veg, "North Indian", "Veg Thali", "150.00", true);

        pizzaPoint = createRestaurant(owner, "Pizza Point", "3.8", true);
        createMenuItem(pizzaPoint, "Pizza", "Veg Pizza", "199.00", true);
        createMenuItem(pizzaPoint, "Biryani", "Paneer Biryani", "180.00", false);

        hidden = createRestaurant(owner, "Hidden Cafe", "5.0", true);
        hidden.setActive(false);
        createMenuItem(hidden, "Biryani", "Biryani Special", "300.00", true);

        entityManager.flush();
        entityManager.clear();
    }

    private ResultActions search(String query) throws Exception {
        return mockMvc.perform(get("/api/restaurants" + query));
    }

    private String[] names(String... names) {
        return names;
    }

    @Test
    void noFiltersReturnsActiveRestaurantsSortedByRatingDescByDefault() throws Exception {
        search("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.contains(
                        "Biryani Blues", "Dosa Corner", "100% Veg", "Pizza Point")))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void keywordMatchesRestaurantNameOrAvailableDishNameCaseInsensitively() throws Exception {
        // Biryani Blues matches. Pizza Point's biryani is unavailable; Hidden Cafe is inactive.
        search("?keyword=BIRYANI")
                .andExpect(jsonPath("$.content[*].name").value(containsInAnyOrder(names("Biryani Blues"))));

        // Matches through the dish name only.
        search("?keyword=thali")
                .andExpect(jsonPath("$.content[*].name").value(containsInAnyOrder(names("100% Veg"))));
    }

    @Test
    void likeWildcardsInKeywordAreTreatedAsPlainText() throws Exception {
        // Unescaped, '%' would match every restaurant and '_' any single character.
        mockMvc.perform(get("/api/restaurants").param("keyword", "%"))
                .andExpect(jsonPath("$.content[*].name").value(containsInAnyOrder(names("100% Veg"))));
        mockMvc.perform(get("/api/restaurants").param("keyword", "_"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void minRatingFilter() throws Exception {
        search("?minRating=4.2")
                .andExpect(jsonPath("$.content[*].name")
                        .value(containsInAnyOrder(names("Biryani Blues", "Dosa Corner"))));
    }

    @Test
    void openFilter() throws Exception {
        search("?open=false")
                .andExpect(jsonPath("$.content[*].name").value(containsInAnyOrder(names("Dosa Corner"))));
    }

    @Test
    void categoryFilterOnlyCountsAvailableDishes() throws Exception {
        Long biryani = category("Biryani").getId();

        search("?categoryId=" + biryani)
                .andExpect(jsonPath("$.content[*].name").value(containsInAnyOrder(names("Biryani Blues"))));
    }

    @Test
    void priceRangeFilter() throws Exception {
        search("?minPrice=100&maxPrice=200")
                .andExpect(jsonPath("$.content[*].name")
                        .value(containsInAnyOrder(names("100% Veg", "Pizza Point"))));
    }

    @Test
    void categoryAndPriceMustMatchTheSameDish() throws Exception {
        // Pizza Point has a pizza (199), but not one costing at most 150.
        search("?categoryId=" + category("Pizza").getId() + "&maxPrice=150")
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void paginationReturnsRequestedSliceAndMetadata() throws Exception {
        search("?page=1&size=2")
                .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.contains("100% Veg", "Pizza Point")))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void sortByNameAscending() throws Exception {
        search("?sort=name,asc")
                .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.contains(
                        "100% Veg", "Biryani Blues", "Dosa Corner", "Pizza Point")));
    }

    @Test
    void sortingByNonWhitelistedPropertyIsRejected() throws Exception {
        search("?sort=owner.passwordHash")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Cannot sort by 'owner.passwordHash'")));
    }

    @Test
    void pageSizeIsCappedAt50() throws Exception {
        search("?size=100000").andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void invalidFilterValuesReturn400() throws Exception {
        search("?minRating=7").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.minRating").exists());
        search("?minRating=abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.minRating").value("Invalid value 'abc'"));
        search("?minPrice=300&maxPrice=100").andExpect(status().isBadRequest());
    }

    @Test
    void searchNeedsAtMostTwoQueriesPageAndCount() throws Exception {
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        String biryaniFilter = "&categoryId=" + category("Biryani").getId();

        // A full page (size=1, 4 matches): SELECT ... LIMIT 1 + SELECT count(*) = 2 statements.
        statistics.clear();
        search("?size=1&minRating=3").andExpect(jsonPath("$.totalElements").value(4));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);

        // First page NOT full (1 match, size 10): the total is already known, so Spring Data
        // skips the count query. 1 statement, even with keyword + rating + category filters.
        // No N+1 either way: owners are never loaded.
        statistics.clear();
        search("?keyword=biryani&minRating=3" + biryaniFilter).andExpect(jsonPath("$.totalElements").value(1));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void inactiveRestaurantIsHiddenFromDetailsAndMenu() throws Exception {
        mockMvc.perform(get("/api/restaurants/" + hidden.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/restaurants/" + hidden.getId() + "/menu")).andExpect(status().isNotFound());
    }

    @Test
    void publicMenuShowsOnlyAvailableDishes() throws Exception {
        mockMvc.perform(get("/api/restaurants/" + pizzaPoint.getId() + "/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Veg Pizza"))
                .andExpect(jsonPath("$[0].categoryName").value("Pizza"))
                .andExpect(jsonPath("$[0].price").value(199.00));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/restaurants/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'abc' for id"));
    }
}
