package com.foodflow.controller;

import com.foodflow.dto.payment.PaymentRequest;
import com.foodflow.dto.payment.PaymentResponse;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.PaymentService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@Tag(name = "Payments")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /*
     * 201 Created even when the card is DECLINED: the request was valid and a payment attempt
     * resource now exists; its status says FAILED. HTTP errors (4xx) are reserved for requests
     * that are wrong (order already paid, not yours, ...), not for a bank saying no.
     */
    @Operation(summary = "Pay an online order; a declined payment is 201 with status FAILED")
    @PostMapping("/api/payments")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentResponse> pay(@AuthenticationPrincipal UserPrincipal me,
                                               @Valid @RequestBody PaymentRequest request) {
        PaymentResponse payment = paymentService.pay(me.getId(), request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(payment.id()).toUri()).body(payment);
    }

    @Operation(summary = "Payment details")
    @GetMapping("/api/payments/{id}")
    public PaymentResponse get(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return paymentService.getPayment(id, me);
    }

    /** Every attempt for an order: declined, retried, refunded... */
    @Operation(summary = "All payment attempts for an order")
    @GetMapping("/api/orders/{orderId}/payments")
    public List<PaymentResponse> forOrder(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long orderId) {
        return paymentService.getPaymentsForOrder(orderId, me);
    }
}
