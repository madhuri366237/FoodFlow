package com.foodflow.service.impl;

import com.foodflow.entity.Order;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.OrderRepository;
import com.foodflow.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Who may see an order, in one place (used by orders AND payments):
 * customer = own orders, owner = orders of own restaurants, admin = all.
 * Anything else is 404, never 403: orders are private, so their existence isn't revealed.
 */
@Component
@RequiredArgsConstructor
class OrderAccess {

    private final OrderRepository orderRepository;

    Order requireVisible(Long orderId, UserPrincipal caller) {
        Order order = orderRepository.findDetailedById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        if (!canSee(order, caller)) {
            throw ResourceNotFoundException.of("Order", orderId);
        }
        return order;
    }

    static boolean canSee(Order order, UserPrincipal caller) {
        return switch (caller.getRole()) {
            case CUSTOMER -> order.getCustomer().getId().equals(caller.getId());
            case RESTAURANT_OWNER -> order.getRestaurant().getOwner().getId().equals(caller.getId());
            case ADMIN -> true;
        };
    }
}
