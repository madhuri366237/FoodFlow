package com.foodflow.service.payment;

/** The gateway was unreachable or returned an error: we don't know whether money moved. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }
}
