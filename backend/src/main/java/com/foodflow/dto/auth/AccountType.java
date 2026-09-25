package com.foodflow.dto.auth;

/**
 * The account types a person may choose when registering themselves.
 *
 * <p>Deliberately a separate enum from {@link com.foodflow.entity.Role}: ADMIN is simply not
 * a value here. A request with "accountType":"ADMIN" fails JSON parsing (400) before any
 * of our code runs, so no if-statement can be forgotten or bypassed.
 */
public enum AccountType {
    CUSTOMER,
    RESTAURANT_OWNER
}
