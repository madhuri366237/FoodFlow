package com.foodflow;

import com.foodflow.entity.Address;
import com.foodflow.entity.Cart;
import com.foodflow.entity.Category;
import com.foodflow.entity.MenuItem;
import com.foodflow.entity.Restaurant;
import com.foodflow.entity.Role;
import com.foodflow.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;

/**
 * In-memory entities for Mockito unit tests: no database, so ids are set by reflection the way
 * the database would assign them.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static User user(long id, Role role) {
        return withId(new User("User " + id, "user" + id + "@example.com", "hash", "9000000000", role), id);
    }

    public static Restaurant restaurant(long id, User owner, String name) {
        return withId(new Restaurant(owner, name, null, "1 Road", "9000000001", null), id);
    }

    public static MenuItem dish(long id, Restaurant restaurant, String name, String price) {
        return withId(new MenuItem(restaurant, new Category("Test", null, null), name, null, new BigDecimal(price), null), id);
    }

    public static Address address(long id, User user) {
        return withId(new Address(user, "Home", "12 MG Road", null, "Bengaluru", "Karnataka", "560001", true), id);
    }

    /** Cart's constructor is protected (JPA-only), like every entity's no-arg constructor. */
    public static Cart emptyCart() {
        try {
            Constructor<Cart> constructor = Cart.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
