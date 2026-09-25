package com.foodflow.service;

import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.order.CreateOrderRequest;
import com.foodflow.dto.order.OrderResponse;
import com.foodflow.dto.order.OrderSummaryResponse;
import com.foodflow.entity.OrderStatus;
import com.foodflow.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    /** Checkout: turns the customer's cart into an order, atomically. */
    OrderResponse placeOrder(Long customerId, CreateOrderRequest request);

    PageResponse<OrderSummaryResponse> getMyOrders(Long customerId, Pageable pageable);

    PageResponse<OrderSummaryResponse> getOwnerOrders(Long ownerId, Long restaurantId, OrderStatus status,
                                                      Pageable pageable);

    /** Admin: every order on the platform, optionally filtered. */
    PageResponse<OrderSummaryResponse> getAllOrders(Long restaurantId, OrderStatus status, Pageable pageable);

    /** Customer (own orders), owner (orders of own restaurants) or admin (all). */
    OrderResponse getOrder(Long orderId, UserPrincipal caller);

    /** Owner or admin moves the order along the state machine. */
    OrderResponse changeStatus(Long orderId, UserPrincipal caller, OrderStatus next, String note);

    /** Customer cancels their own order (only while PLACED). */
    OrderResponse cancelByCustomer(Long orderId, Long customerId, String reason);
}
