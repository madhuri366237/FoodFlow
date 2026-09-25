package com.foodflow.exception;

import org.springframework.http.HttpStatus;

/**
 * 400 with a specific code, so the UI can show the right message:
 * INVALID_COUPON, COUPON_EXPIRED, COUPON_NOT_STARTED, COUPON_MINIMUM_NOT_MET,
 * COUPON_USAGE_LIMIT_REACHED.
 */
public class InvalidCouponException extends ApiException {

    public InvalidCouponException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
