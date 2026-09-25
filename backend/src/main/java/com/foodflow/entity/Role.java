package com.foodflow.entity;

/**
 * Account type. Stored as its name (e.g. 'CUSTOMER') in users.role.
 *
 * <p>Never reorder-sensitive: we persist with {@code EnumType.STRING}, not ORDINAL.
 * With ORDINAL, inserting a new constant in the middle would silently turn every
 * existing ADMIN row into a different role.
 */
public enum Role {
    CUSTOMER,
    RESTAURANT_OWNER,
    ADMIN
}
