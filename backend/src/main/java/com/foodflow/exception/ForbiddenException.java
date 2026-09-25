package com.foodflow.exception;

import org.springframework.http.HttpStatus;

/**
 * 403: the caller is authenticated and has the right role, but this particular resource
 * isn't theirs, e.g. an owner editing another owner's restaurant.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
