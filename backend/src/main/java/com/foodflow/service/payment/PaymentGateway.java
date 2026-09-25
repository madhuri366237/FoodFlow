package com.foodflow.service.payment;

import java.math.BigDecimal;

/**
 * The boundary between FoodFlow and whoever actually moves the money.
 *
 * <p>The rest of the application depends only on this interface (dependency inversion), never
 * on Razorpay/Stripe classes. Switching providers therefore means writing ONE new
 * implementation and changing ONE property (app.payment.provider); no service, controller,
 * entity or test outside this package changes.
 *
 * <p>Implementations talk to the outside world, so callers must never invoke them inside a
 * database transaction (see PaymentServiceImpl).
 */
public interface PaymentGateway {

    /** Name stored in payments.provider, e.g. "SIMULATED", "RAZORPAY". */
    String name();

    /**
     * Charge the customer.
     *
     * @return SUCCESS with the gateway's transaction reference, or FAILED with a reason
     *         (declined card, insufficient funds, ...): an expected business outcome
     * @throws PaymentGatewayException if the gateway could not be reached or answered with an
     *         error. The outcome is then unknown, which is a technical failure, not a decline.
     */
    GatewayResult charge(GatewayChargeRequest request);

    /** Refund a previously successful charge, identified by its transaction reference. */
    GatewayResult refund(String transactionReference, BigDecimal amount);
}
