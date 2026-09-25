package com.foodflow.exception;

import org.springframework.http.HttpStatus;

/**
 * 409: the request conflicts with the current state, e.g. the email is already registered,
 * or the cart holds dishes from another restaurant.
 *
 * <p>A specific error code (e.g. "CART_RESTAURANT_MISMATCH") lets the frontend react
 * precisely, such as offering "Clear cart and add this item?", without parsing
 * human-readable messages.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        this("CONFLICT", message);
    }

    public ConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
