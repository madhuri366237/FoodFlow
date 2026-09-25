package com.foodflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A customer's rating (1-5) and optional comment for one delivered order.
 * Relationships: many reviews -> one customer, many -> one restaurant, exactly one -> one order.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reviews")
public class Review extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    // @OneToOne: the UNIQUE(order_id) constraint makes it one review per order.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    public Review(User customer, Restaurant restaurant, Order order, int rating, String comment) {
        this.customer = customer;
        this.restaurant = restaurant;
        this.order = order;
        this.rating = rating;
        this.comment = comment;
    }
}
