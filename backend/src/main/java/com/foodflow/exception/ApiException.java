package com.foodflow.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for business errors we raise on purpose. Each subclass fixes its HTTP status
 * and error code, so GlobalExceptionHandler needs only one handler for all of them and
 * services never deal with HTTP details.
 *
 * <p>Unchecked (RuntimeException) so that a failure inside a {@code @Transactional}
 * service method rolls the transaction back by default.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
