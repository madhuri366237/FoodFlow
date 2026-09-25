package com.foodflow.entity;

/**
 * Status of ONE payment attempt.
 * <pre>
 * PENDING ──► SUCCESS ──► REFUNDED
 *    │
 *    ├──────► FAILED       (declined / gateway error; the customer may retry)
 *    └──────► CANCELLED    (cash-on-delivery order cancelled before delivery)
 * </pre>
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED,
    CANCELLED
}
