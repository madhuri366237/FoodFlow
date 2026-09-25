package com.foodflow.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A customer's cart: the "aggregate root" for its lines.
 *
 * <p>This is the first {@code @OneToMany} in the project, and a deliberate one. Cart lines
 * have no life of their own: they are only ever read, added or removed THROUGH their cart,
 * and a cart holds at most a handful of them. That is exactly the case where a mapped
 * collection helps:
 * <ul>
 *   <li>{@code cascade = ALL}: saving the cart saves new lines.</li>
 *   <li>{@code orphanRemoval = true}: removing a line from the list DELETEs its row.</li>
 * </ul>
 * Compare Restaurant -> MenuItem: unbounded and paged, so it has no collection.
 *
 * <p>All changes go through methods on this class, so the invariant "restaurant is null
 * exactly when the cart is empty" is kept in one place.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "carts")
public class Cart extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // The restaurant all lines belong to; null while empty.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CartItem> items = new ArrayList<>();

    /** Read-only view: callers can't bypass addItem/removeItem and break the invariant. */
    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Optional<CartItem> findItem(Long cartItemId) {
        return items.stream().filter(item -> cartItemId.equals(item.getId())).findFirst();
    }

    public Optional<CartItem> findItemForMenuItem(Long menuItemId) {
        return items.stream().filter(item -> menuItemId.equals(item.getMenuItem().getId())).findFirst();
    }

    /** Adds a new line. The service has already checked the restaurant rule. */
    public void addItem(MenuItem menuItem, int quantity) {
        if (items.isEmpty()) {
            this.restaurant = menuItem.getRestaurant();
        }
        items.add(new CartItem(this, menuItem, quantity));
    }

    public void removeItem(CartItem item) {
        items.remove(item);
        if (items.isEmpty()) {
            this.restaurant = null;
        }
    }

    public void clear() {
        items.clear();
        this.restaurant = null;
    }
}
