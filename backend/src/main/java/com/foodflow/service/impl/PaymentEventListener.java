package com.foodflow.service.impl;

import com.foodflow.service.PaymentService;
import com.foodflow.service.payment.RefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT: the refund runs only once the cancellation is really saved.
 * If the cancel transaction rolls back, the event is dropped, so we never refund an order
 * that is still active. The gateway call also stays outside the order's transaction.
 */
@Component
@RequiredArgsConstructor
class PaymentEventListener {

    private final PaymentService paymentService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onRefundRequested(RefundRequestedEvent event) {
        paymentService.refundIfPaid(event.orderId());
    }
}
