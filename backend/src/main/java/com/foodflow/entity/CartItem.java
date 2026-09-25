package com.foodflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One line of a cart: which dish, how many. There is intentionally no price field;
 * the current price is always read from {@link MenuItem#getPrice()}.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "cart_items")
public class CartItem extends BaseEntity {

    public static final int MAX_QUANTITY = 20;

    // The back-reference that makes Cart.items bidirectional. It is never serialized
    // (DTOs only), so there is no infinite JSON recursion.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false)
    private int quantity;

    CartItem(Cart cart, MenuItem menuItem, int quantity) {
        this.cart = cart;
        this.menuItem = menuItem;
        this.quantity = quantity;
    }

    public void changeQuantity(int quantity) {
        this.quantity = quantity;
    }
}
