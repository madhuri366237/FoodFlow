package com.foodflow.exception;

import com.foodflow.entity.OrderStatus;
import org.springframework.http.HttpStatus;

/** 409: the requested status change is not a legal move in the order state machine. */
public class InvalidOrderStatusException extends ApiException {

    public InvalidOrderStatusException(OrderStatus from, OrderStatus to) {
        super(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                "Cannot change order status from %s to %s. Allowed next: %s"
                        .formatted(from, to, from.allowedNext()));
    }
}
