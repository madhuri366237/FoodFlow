package com.foodflow.entity;

import com.foodflow.util.MoneyUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One line of an order, with a SNAPSHOT of the dish as it was at checkout.
 * Immutable after creation: no setters. History is never edited.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Reference only (e.g. "order again"). Becomes NULL if the dish is deleted later;
    // the snapshot fields below are what the order actually shows.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id")
    private MenuItem menuItem;

    @Column(name = "item_name", nullable = false, length = 120)
    private String itemName;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    OrderItem(Order order, MenuItem menuItem, String itemName, BigDecimal unitPrice, int quantity) {
        this.order = order;
        this.menuItem = menuItem;
        this.itemName = itemName;
        this.unitPrice = MoneyUtils.normalize(unitPrice);
        this.quantity = quantity;
        this.lineTotal = MoneyUtils.lineTotal(unitPrice, quantity);
    }
}
