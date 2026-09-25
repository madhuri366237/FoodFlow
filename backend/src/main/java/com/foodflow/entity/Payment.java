package com.foodflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One payment attempt for an order. Many attempts per order are possible (declined, then
 * retried), but the database allows only one that is PENDING/SUCCESS/REFUNDED at a time.
 *
 * <p>Card data is never stored or even received: the client sends only an opaque token issued
 * by the gateway's own SDK (see PaymentRequest). This keeps the backend out of PCI-DSS scope.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(name = "refund_reference", length = 100)
    private String refundReference;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    /** Starts as PENDING; the amount is always the order's server-computed total. */
    public Payment(Order order, PaymentMethod method, String provider) {
        this.order = order;
        this.amount = order.getTotalAmount();
        this.method = method;
        this.provider = provider;
        this.status = PaymentStatus.PENDING;
    }

    public void markSucceeded(String transactionReference) {
        requireStatus(PaymentStatus.PENDING);
        this.status = PaymentStatus.SUCCESS;
        this.transactionReference = transactionReference;
    }

    public void markFailed(String reason) {
        requireStatus(PaymentStatus.PENDING);
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void markCancelled(String reason) {
        requireStatus(PaymentStatus.PENDING);
        this.status = PaymentStatus.CANCELLED;
        this.failureReason = reason;
    }

    public void markRefunded(String refundReference) {
        requireStatus(PaymentStatus.SUCCESS);
        this.status = PaymentStatus.REFUNDED;
        this.refundReference = refundReference;
    }

    /** Guards against programming errors such as refunding a payment that never succeeded. */
    private void requireStatus(PaymentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Payment " + getId() + " is " + status + ", expected " + expected);
        }
    }
}
