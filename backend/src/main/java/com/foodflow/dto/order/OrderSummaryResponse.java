package com.foodflow.dto.order;

import com.foodflow.entity.Order;
import com.foodflow.entity.OrderPaymentStatus;
import com.foodflow.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of an order list ("My Orders", owner dashboard): no lines or history. */
public record OrderSummaryResponse(
        Long id,
        Long restaurantId,
        String restaurantName,
        String customerName,
        OrderStatus status,
        OrderPaymentStatus paymentStatus,
        int itemCount,
        BigDecimal totalAmount,
        Instant createdAt) {

    /**
     * Expects restaurant and customer to be fetched with the page query (@EntityGraph).
     * getItems() is lazy; the batch fetch size loads the items of the whole page in one query.
     */
    public static OrderSummaryResponse from(Order order) {
        int itemCount = order.getItems().stream().mapToInt(item -> item.getQuantity()).sum();
        return new OrderSummaryResponse(order.getId(), order.getRestaurant().getId(),
                order.getRestaurant().getName(), order.getCustomer().getName(), order.getStatus(),
                order.getPaymentStatus(), itemCount, order.getTotalAmount(), order.getCreatedAt());
    }
}
