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
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A dish on a restaurant's menu.
 * Relationships: many menu items -> one restaurant; many menu items -> one category.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "menu_items")
public class MenuItem extends BaseEntity {

    // Both LAZY. When a query needs them, it asks explicitly (@EntityGraph / JOIN FETCH)
    // so the data arrives in the same SQL statement - see MenuItemRepository.
    @Setter(AccessLevel.NONE) // a dish never moves to another restaurant
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    // BigDecimal, never double: 0.1 + 0.2 != 0.3 in floating point.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean available = true;

    public MenuItem(Restaurant restaurant, Category category, String name,
                    String description, BigDecimal price, String imageUrl) {
        this.restaurant = restaurant;
        this.category = category;
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
    }
}
