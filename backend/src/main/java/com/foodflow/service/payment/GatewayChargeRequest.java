package com.foodflow.service.payment;

import com.foodflow.entity.PaymentMethod;

import java.math.BigDecimal;

/**
 * What we ask the gateway to charge.
 *
 * @param idempotencyKey unique per payment attempt. If a network timeout makes us retry, a real
 *                       gateway sees the same key and returns the first result instead of
 *                       charging the customer twice.
 * @param paymentToken   opaque token produced by the gateway's client-side SDK; stands in for
 *                       the card/UPI details, which never reach our server
 */
public record GatewayChargeRequest(
        String idempotencyKey,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        String paymentToken) {
}
