package com.foodflow.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * POST /api/payments body: {"orderId": 5, "paymentToken": "tok_visa"}.
 *
 * <p>No amount: we charge the order's server-computed total, never a client number.
 * No card number, CVV or UPI PIN either. In production, the gateway's JavaScript SDK collects
 * those in the browser and returns an opaque token; only that token reaches our backend.
 * The payment method was chosen at checkout and is read from the order.
 */
public record PaymentRequest(

        @NotNull(message = "orderId is required")
        @Positive(message = "orderId must be positive")
        @Schema(example = "1")
        Long orderId,

        @NotBlank(message = "paymentToken is required")
        @Size(max = 200, message = "paymentToken must be at most 200 characters")
        @Schema(example = "tok_visa", description = "Simulator: tok_visa succeeds; tok_chargeDeclined, tok_insufficientFunds fail; tok_timeout = gateway down")
        String paymentToken) {
}
