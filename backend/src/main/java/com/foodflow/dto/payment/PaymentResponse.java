package com.foodflow.dto.payment;

import com.foodflow.entity.Payment;
import com.foodflow.entity.PaymentMethod;
import com.foodflow.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String provider,
        String transactionReference,
        String refundReference,
        String failureReason,
        Instant createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrder().getId(), payment.getAmount(),
                payment.getMethod(), payment.getStatus(), payment.getProvider(), payment.getTransactionReference(),
                payment.getRefundReference(), payment.getFailureReason(), payment.getCreatedAt());
    }
}
