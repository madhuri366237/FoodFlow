package com.foodflow.exception;

import org.springframework.http.HttpStatus;

/** 400: the request is well-formed JSON but breaks a business rule. */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
