package com.foodflow.dto.order;

import com.foodflow.entity.OrderStatus;
import com.foodflow.entity.OrderStatusHistory;

import java.time.Instant;

/** One step of the tracking timeline. */
public record OrderStatusHistoryResponse(OrderStatus status, String note, Instant at) {

    public static OrderStatusHistoryResponse from(OrderStatusHistory entry) {
        return new OrderStatusHistoryResponse(entry.getToStatus(), entry.getNote(), entry.getCreatedAt());
    }
}
