package com.foodflow.service.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A fake gateway for development and demos. No money moves.
 *
 * <p>Outcomes are driven by the payment token, the way Stripe's test mode uses magic test
 * cards, so every scenario can be reproduced on demand:
 * <pre>
 *   token contains "declined"      -> FAILED  "Card declined"
 *   token contains "insufficient"  -> FAILED  "Insufficient funds"
 *   token contains "timeout"       -> PaymentGatewayException (gateway unreachable)
 *   anything else                  -> SUCCESS (e.g. "tok_visa", "upi_success")
 * </pre>
 * On top of that, app.payment.simulated.failure-rate (0.0 - 1.0) fails that fraction of
 * otherwise successful charges at random, to demo the retry flow. Default 0: deterministic.
 *
 * <p>Active when app.payment.provider=simulated (the default). A future RazorpayPaymentGateway
 * would be annotated {@code @ConditionalOnProperty(..., havingValue = "razorpay")}, and exactly
 * one gateway bean exists at runtime.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGateway {

    private final double failureRate;

    public SimulatedPaymentGateway(@Value("${app.payment.simulated.failure-rate:0.0}") double failureRate) {
        if (failureRate < 0 || failureRate > 1) {
            throw new IllegalArgumentException("app.payment.simulated.failure-rate must be between 0 and 1");
        }
        this.failureRate = failureRate;
    }

    @Override
    public String name() {
        return "SIMULATED";
    }

    @Override
    public GatewayResult charge(GatewayChargeRequest request) {
        String token = request.paymentToken() == null ? "" : request.paymentToken().toLowerCase(Locale.ROOT);
        log.info("[SIMULATED] charge {} {} via {} (idempotency key {})",
                request.amount(), request.currency(), request.method(), request.idempotencyKey());

        if (token.contains("timeout")) {
            throw new PaymentGatewayException("Simulated gateway timeout");
        }
        if (token.contains("declined")) {
            return GatewayResult.failure("Card declined");
        }
        if (token.contains("insufficient")) {
            return GatewayResult.failure("Insufficient funds");
        }
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            return GatewayResult.failure("Payment failed (simulated random failure)");
        }
        return GatewayResult.success("SIM_TXN_" + UUID.randomUUID());
    }

    @Override
    public GatewayResult refund(String transactionReference, BigDecimal amount) {
        log.info("[SIMULATED] refund {} of {}", amount, transactionReference);
        return GatewayResult.success("SIM_RFND_" + UUID.randomUUID());
    }
}
