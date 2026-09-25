package com.foodflow.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * The order state machine. The ONLY legal moves are listed in {@link #allowedNext()}:
 *
 * <pre>
 * PLACED ──► CONFIRMED ──► PREPARING ──► READY_FOR_PICKUP ──► OUT_FOR_DELIVERY ──► DELIVERED
 *    │
 *    └─────► CANCELLED
 * </pre>
 *
 * DELIVERED and CANCELLED are terminal: nothing can follow them. Every other move
 * (e.g. DELIVERED -> PREPARING, or skipping PLACED -> DELIVERED) is rejected.
 *
 * <p>Cancellation is only possible from PLACED, i.e. before the restaurant has accepted the
 * order and started spending money on it.
 */
public enum OrderStatus {
    PLACED,
    CONFIRMED,
    PREPARING,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    /*
     * A switch over "this" rather than a Map filled in constructors: an enum constant cannot
     * refer to constants declared after it in its own constructor, and the exhaustive switch
     * makes the compiler complain if a new status is added without deciding its transitions.
     */
    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PLACED -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(PREPARING);
            case PREPARING -> EnumSet.of(READY_FOR_PICKUP);
            case READY_FOR_PICKUP -> EnumSet.of(OUT_FOR_DELIVERY);
            case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
