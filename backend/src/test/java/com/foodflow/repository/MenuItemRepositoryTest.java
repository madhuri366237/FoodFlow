package com.foodflow.repository;

import com.foodflow.entity.Category;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MenuItemRepositoryTest extends RepositoryTestBase {

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Statistics statistics;
    private User owner;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        statistics = em.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        owner = persistUser("owner@example.com", Role.RESTAURANT_OWNER);
        restaurant = persistRestaurant(owner, "Spice Hub");
    }

    /** Three items, each in a different category - the worst case for N+1. */
    private void persistMenuWithThreeCategories() {
        persistMenuItem(restaurant, persistCategory("Test Biryani"), "Paneer Biryani", "180.00");
        persistMenuItem(restaurant, persistCategory("Test Pizza"), "Veg Pizza", "250.00");
        persistMenuItem(restaurant, persistCategory("Test Beverages"), "Coke", "50.00");
        flushAndClear();
        statistics.clear();
    }

    @Test
    void entityGraphLoadsMenuAndCategoriesInOneQuery() {
        persistMenuWithThreeCategories();

        List<MenuItem> menu = menuItemRepository.findByRestaurantIdOrderByNameAsc(restaurant.getId());
        List<String> categoryNames = menu.stream().map(item -> item.getCategory().getName()).toList();

        assertThat(categoryNames).containsExactly("Test Beverages", "Test Biryani", "Test Pizza");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void withoutEntityGraphBatchFetchingTurnsNPlusOneIntoOnePlusOne() {
        persistMenuWithThreeCategories();

        // findAll has no entity graph: 1 query for the items...
        List<MenuItem> menu = menuItemRepository.findAll();
        // ...then touching the lazy categories. Without hibernate.default_batch_fetch_size this
        // is 1 query PER category (N+1 = 1 + 3). With it (application.yml, Phase 5), Hibernate
        // loads all pending categories in ONE "where id in (?, ?, ?)" query.
        menu.forEach(item -> item.getCategory().getName());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1 + 1);
    }

    @Test
    void customerMenuExcludesUnavailableItems() {
        Category category = persistCategory("Test Desserts");
        persistMenuItem(restaurant, category, "Gulab Jamun", "90.00");
        MenuItem soldOut = persistMenuItem(restaurant, category, "Rasmalai", "120.00");
        soldOut.setAvailable(false);
        flushAndClear();

        assertThat(menuItemRepository.findByRestaurantIdAndAvailableTrueOrderByNameAsc(restaurant.getId()))
                .extracting(MenuItem::getName)
                .containsExactly("Gulab Jamun");
        assertThat(menuItemRepository.findByRestaurantIdOrderByNameAsc(restaurant.getId())).hasSize(2);
    }

    @Test
    void priceMustBePositive() {
        MenuItem free = new MenuItem(restaurant, persistCategory("Free"), "Water", null, BigDecimal.ZERO, null);

        assertThatThrownBy(() -> menuItemRepository.saveAndFlush(free))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_menu_items_price_positive");
    }

    @Test
    void sameDishNameTwiceInOneRestaurantIsRejectedCaseInsensitively() {
        Category category = persistCategory("Test Pizza");
        persistMenuItem(restaurant, category, "Veg Pizza", "250.00");
        em.flush();

        assertThat(menuItemRepository.existsByRestaurantIdAndNameIgnoreCase(restaurant.getId(), "VEG PIZZA")).isTrue();
        assertThatThrownBy(() -> menuItemRepository.saveAndFlush(
                new MenuItem(restaurant, category, "VEG PIZZA", null, new BigDecimal("260.00"), null)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_menu_items_restaurant_name");
    }

    @Test
    void sameDishNameInDifferentRestaurantsIsAllowed() {
        Category category = persistCategory("Test Pizza");
        Restaurant other = persistRestaurant(persistUser("other@example.com", Role.RESTAURANT_OWNER), "Pizza Point");

        persistMenuItem(restaurant, category, "Veg Pizza", "250.00");
        persistMenuItem(other, category, "Veg Pizza", "230.00");
        em.flush();

        assertThat(menuItemRepository.count()).isEqualTo(2);
    }

    @Test
    void ownershipLookupFetchesRestaurantInTheSameQuery() {
        MenuItem item = persistMenuItem(restaurant, persistCategory("Test Biryani"), "Chicken Biryani", "220.00");
        flushAndClear();
        statistics.clear();

        MenuItem loaded = menuItemRepository.findWithRestaurantById(item.getId()).orElseThrow();

        // RestaurantAccess compares item.restaurant.owner.id with the caller: 1 query, no lazy loads.
        assertThat(org.hibernate.Hibernate.isInitialized(loaded.getRestaurant())).isTrue();
        assertThat(loaded.getRestaurant().getOwner().getId()).isEqualTo(owner.getId());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void categoryInUseCannotBeDeleted() {
        Category category = persistCategory("Test Starters");
        persistMenuItem(restaurant, category, "Paneer Tikka", "200.00");
        // Clear first, like a fresh request would. Otherwise Hibernate sees the managed MenuItem
        // still pointing at the category and refuses in Java (TransientObjectException) before
        // any SQL runs - and we want to prove the DATABASE foreign key blocks the delete.
        flushAndClear();

        assertThat(menuItemRepository.existsByCategoryId(category.getId())).isTrue();
        assertThatThrownBy(() -> {
            categoryRepository.deleteById(category.getId());
            categoryRepository.flush();
        })
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_menu_items_category");
    }

    @Test
    void referenceCategoriesAreInsertedByMigration() {
        assertThat(categoryRepository.findByNameIgnoreCase("biryani")).isPresent();
        assertThat(categoryRepository.findAllByOrderByNameAsc()).hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    void categoryNamesAreUniqueIgnoringCase() {
        categoryRepository.saveAndFlush(new Category("Momos", null, null));

        assertThat(categoryRepository.existsByNameIgnoreCase("MOMOS")).isTrue();
        assertThatThrownBy(() -> categoryRepository.saveAndFlush(new Category("momos", null, null)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_categories_name_lower");
    }
}
