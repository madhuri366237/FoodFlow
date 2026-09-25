package com.foodflow.entity;

/** The order's overall money state (as opposed to the status of individual payment attempts). */
public enum OrderPaymentStatus {
    PENDING,
    PAID,
    REFUNDED
}
