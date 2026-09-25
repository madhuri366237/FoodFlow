package com.foodflow.controller;

import org.springdoc.core.annotations.ParameterObject;
import com.foodflow.dto.common.PageResponse;
import com.foodflow.dto.order.CancelOrderRequest;
import com.foodflow.dto.order.CreateOrderRequest;
import com.foodflow.dto.order.OrderResponse;
import com.foodflow.dto.order.OrderSummaryResponse;
import com.foodflow.dto.order.UpdateOrderStatusRequest;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.OrderService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Orders")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** Checkout: 201 Created + Location of the new order. */
    @Operation(summary = "Checkout: build an order from the cart (prices, coupon and totals computed on the server)")
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> place(@AuthenticationPrincipal UserPrincipal me,
                                               @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse order = orderService.placeOrder(me.getId(), request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(order.id()).toUri()).body(order);
    }

    /** "My Orders", newest first. */
    @Operation(summary = "My orders, newest first")
    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public PageResponse<OrderSummaryResponse> myOrders(
            @AuthenticationPrincipal UserPrincipal me,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getMyOrders(me.getId(), pageable);
    }

    /** Any role; visibility is decided per order in the service (own / own restaurant / admin). */
    @Operation(summary = "Order details and tracking timeline (its customer, the restaurant owner, or an admin)")
    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return orderService.getOrder(id, me);
    }

    @Operation(summary = "Cancel your own order while it is still PLACED")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public OrderResponse cancel(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancelByCustomer(id, me.getId(), request == null ? null : request.reason());
    }

    /** The restaurant (or an admin) moves the order forward. The state machine decides what is legal. */
    @Operation(summary = "Move an order along the state machine (owner/admin); illegal moves return 409")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    public OrderResponse changeStatus(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.changeStatus(id, me, request.status(), request.note());
    }
}
