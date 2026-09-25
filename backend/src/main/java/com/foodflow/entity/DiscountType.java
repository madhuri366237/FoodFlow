package com.foodflow.entity;

public enum DiscountType {
    /** discount_value is a percentage of the subtotal (1-100), usually with a maximum_discount cap. */
    PERCENTAGE,
    /** discount_value is an amount in rupees. */
    FIXED_AMOUNT
}
