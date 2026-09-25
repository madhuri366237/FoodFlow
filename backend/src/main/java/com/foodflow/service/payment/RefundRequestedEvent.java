package com.foodflow.service.payment;

/**
 * "This order no longer needs the money it was paid." Published by the order side when a paid
 * order is cancelled; handled after that transaction commits (PaymentEventListener).
 *
 * <p>The order code doesn't know HOW refunds work, or even that a gateway exists. It states a
 * fact, and the payment side reacts.
 */
public record RefundRequestedEvent(Long orderId) {
}
