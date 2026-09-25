package com.foodflow.service;

import com.foodflow.dto.payment.PaymentRequest;
import com.foodflow.dto.payment.PaymentResponse;
import com.foodflow.entity.Order;
import com.foodflow.security.UserPrincipal;

import java.util.List;

public interface PaymentService {

    /** Customer pays an online (CARD/UPI) order. Declines are returned as a FAILED payment, not an error. */
    PaymentResponse pay(Long customerId, PaymentRequest request);

    PaymentResponse getPayment(Long paymentId, UserPrincipal caller);

    List<PaymentResponse> getPaymentsForOrder(Long orderId, UserPrincipal caller);

    // ---- Called by the order side, inside ITS transaction ----

    /** Checkout with cash on delivery: record the payment we expect to collect. */
    void createCashOnDeliveryPayment(Order order);

    /** Delivered: the rider collected the cash. */
    void recordCashCollected(Order order);

    /** Cancelled before delivery: the expected cash will never be collected. */
    void cancelPendingCashPayment(Order order);

    // ---- Called after an order transaction commits ----

    /** Refunds the order's successful online payment, if there is one. Never throws. */
    void refundIfPaid(Long orderId);
}
