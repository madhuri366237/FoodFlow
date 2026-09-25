package com.foodflow.entity;

import com.foodflow.exception.ConflictException;
import com.foodflow.exception.InvalidOrderStatusException;
import com.foodflow.util.MoneyUtils;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A placed order: an aggregate root owning its lines and its status history.
 *
 * <p>There are no public setters. State changes go through {@link #changeStatus}, which
 * enforces the state machine, and totals are always derived from the lines. Nothing
 * outside this class can put an order into an illegal state or make its total disagree
 * with its items. (The database double-checks the total with a CHECK constraint.)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private Address address;

    // Snapshot: the text the rider needs, frozen at checkout.
    @Column(name = "delivery_address", nullable = false, length = 600)
    private String deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal = MoneyUtils.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = MoneyUtils.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = MoneyUtils.ZERO;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    // The coupon used (reference) + its code at the time (snapshot).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    @Column(name = "coupon_code", length = 30)
    private String couponCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private OrderPaymentStatus paymentStatus;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    /** A new order starts as PLACED and unpaid, with its first history entry. */
    public Order(User customer, Restaurant restaurant, Address address, String deliveryAddress,
                 PaymentMethod paymentMethod) {
        this.customer = customer;
        this.restaurant = restaurant;
        this.address = address;
        this.deliveryAddress = deliveryAddress;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = OrderPaymentStatus.PENDING;
        this.status = OrderStatus.PLACED;
        this.statusHistory.add(new OrderStatusHistory(this, null, OrderStatus.PLACED, customer, null));
    }

    /**
     * May the restaurant start working on this order? Cash orders: yes (paid on delivery).
     * Online orders: only once the money has actually been received.
     */
    public boolean isPaymentSettledForConfirmation() {
        return paymentMethod == PaymentMethod.CASH_ON_DELIVERY || paymentStatus == OrderPaymentStatus.PAID;
    }

    public void markPaid() {
        this.paymentStatus = OrderPaymentStatus.PAID;
    }

    public void markRefunded() {
        this.paymentStatus = OrderPaymentStatus.REFUNDED;
    }

    /** Copies name and CURRENT price from the menu item: the price snapshot. */
    public void addItem(MenuItem menuItem, int quantity) {
        items.add(new OrderItem(this, menuItem, menuItem.getName(), menuItem.getPrice(), quantity));
        recalculateTotals();
    }

    /** Called after all lines are added; the discount was computed from this order's subtotal. */
    public void applyCoupon(Coupon coupon, BigDecimal discount) {
        this.coupon = coupon;
        this.couponCode = coupon.getCode();
        this.discountAmount = MoneyUtils.normalize(discount);
        recalculateTotals();
    }

    private void recalculateTotals() {
        this.subtotal = items.stream().map(OrderItem::getLineTotal).reduce(MoneyUtils.ZERO, BigDecimal::add);
        this.totalAmount = MoneyUtils.normalize(subtotal.subtract(discountAmount));
    }

    /**
     * The only way to change status. Rejects every move the state machine doesn't allow,
     * and records who made the change and when.
     */
    public void changeStatus(OrderStatus next, User changedBy, String note) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidOrderStatusException(status, next);
        }
        if (next == OrderStatus.CONFIRMED && !isPaymentSettledForConfirmation()) {
            throw new ConflictException("PAYMENT_PENDING",
                    "Order " + getId() + " cannot be confirmed until its " + paymentMethod + " payment succeeds");
        }
        statusHistory.add(new OrderStatusHistory(this, status, next, changedBy, note));
        if (next == OrderStatus.CANCELLED) {
            this.cancellationReason = note;
        }
        this.status = next;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<OrderStatusHistory> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }
}
