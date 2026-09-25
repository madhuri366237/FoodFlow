package com.foodflow.dto.order;

import com.foodflow.entity.OrderPaymentStatus;
import com.foodflow.entity.OrderStatus;
import com.foodflow.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Full order details (tracking page, owner's order screen).
 *
 * @param allowedTransitions the statuses the CALLER may move this order to right now. The UI
 *                           shows exactly these buttons, and the server enforces the same rules
 *                           again when a button is used.
 */
public record OrderResponse(
        Long id,
        OrderStatus status,
        Long restaurantId,
        String restaurantName,
        Long customerId,
        String customerName,
        String customerPhone,
        String deliveryAddress,
        List<OrderItemResponse> items,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        String couponCode,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        OrderPaymentStatus paymentStatus,
        String cancellationReason,
        List<OrderStatusHistoryResponse> statusHistory,
        Set<OrderStatus> allowedTransitions,
        boolean reviewed,
        Instant createdAt,
        Instant updatedAt) {
}
