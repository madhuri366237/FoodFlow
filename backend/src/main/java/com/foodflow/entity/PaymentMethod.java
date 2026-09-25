package com.foodflow.entity;

public enum PaymentMethod {
    CARD,
    UPI,
    CASH_ON_DELIVERY;

    /** Online methods are charged through the gateway before the restaurant confirms. */
    public boolean isOnline() {
        return this != CASH_ON_DELIVERY;
    }
}
