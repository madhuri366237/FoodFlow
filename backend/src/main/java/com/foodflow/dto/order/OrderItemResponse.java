package com.foodflow.dto.order;

import com.foodflow.entity.OrderItem;

import java.math.BigDecimal;

/** An order line, straight from the snapshot columns; never from the current menu. */
public record OrderItemResponse(
        Long id,
        Long menuItemId,
        String name,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal) {

    public static OrderItemResponse from(OrderItem item) {
        // getMenuItem().getId() reads the FK from the proxy (no query); null if the dish was deleted.
        Long menuItemId = item.getMenuItem() == null ? null : item.getMenuItem().getId();
        return new OrderItemResponse(item.getId(), menuItemId, item.getItemName(), item.getUnitPrice(),
                item.getQuantity(), item.getLineTotal());
    }
}
