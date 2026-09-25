package com.foodflow.exception;

import org.springframework.http.HttpStatus;

/** 429: too many attempts in a short time (e.g. failed logins). */
public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", message);
    }
}
