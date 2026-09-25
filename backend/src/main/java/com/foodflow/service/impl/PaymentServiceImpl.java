package com.foodflow.service.impl;

import com.foodflow.dto.payment.PaymentRequest;
import com.foodflow.dto.payment.PaymentResponse;
import com.foodflow.entity.Order;
import com.foodflow.entity.Payment;
import com.foodflow.entity.PaymentMethod;
import com.foodflow.entity.PaymentStatus;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.PaymentRepository;
import com.foodflow.security.UserPrincipal;
import com.foodflow.service.PaymentService;
import com.foodflow.service.payment.GatewayChargeRequest;
import com.foodflow.service.payment.GatewayResult;
import com.foodflow.service.payment.PaymentGateway;
import com.foodflow.service.payment.PaymentGatewayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String CURRENCY = "INR";
    private static final String CASH_PROVIDER = "CASH";

    private final PaymentTransactions transactions;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final OrderAccess orderAccess;

    /*
     * Deliberately NOT @Transactional. The gateway call is an external network request that can
     * take seconds, hang, or time out. Inside a transaction it would hold a database connection
     * (and row locks) for that whole time, and a database rollback can't undo a charge anyway.
     *
     *   tx 1 (short):  validate + INSERT payment PENDING           -> COMMIT
     *   no tx:         gateway.charge(...)                          (seconds)
     *   tx 2 (short):  payment SUCCESS/FAILED (+ order PAID)       -> COMMIT
     */
    @Override
    public PaymentResponse pay(Long customerId, PaymentRequest request) {
        PaymentTransactions.StartedPayment started = transactions.start(customerId, request.orderId());

        GatewayResult result;
        try {
            result = gateway.charge(new GatewayChargeRequest("payment-" + started.paymentId(), started.amount(),
                    CURRENCY, started.method(), request.paymentToken()));
        } catch (PaymentGatewayException e) {
            // Technical failure: the outcome is unknown. With the simulator nothing was charged;
            // with a real gateway this is where reconciliation (query by idempotency key) would go.
            log.warn("Gateway error for payment {}: {}", started.paymentId(), e.getMessage());
            result = GatewayResult.failure("Payment provider unavailable. Please try again.");
        }

        return transactions.complete(started.paymentId(), result);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId, UserPrincipal caller) {
        Payment payment = paymentRepository.findWithOrderById(paymentId)
                .filter(p -> OrderAccess.canSee(p.getOrder(), caller))
                .orElseThrow(() -> ResourceNotFoundException.of("Payment", paymentId));
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsForOrder(Long orderId, UserPrincipal caller) {
        orderAccess.requireVisible(orderId, caller);
        return paymentRepository.findByOrderIdOrderByIdAsc(orderId).stream().map(PaymentResponse::from).toList();
    }

    @Override
    @Transactional
    public void createCashOnDeliveryPayment(Order order) {
        paymentRepository.save(new Payment(order, PaymentMethod.CASH_ON_DELIVERY, CASH_PROVIDER));
    }

    @Override
    @Transactional
    public void recordCashCollected(Order order) {
        Payment payment = paymentRepository.findFirstByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING)
                .orElseGet(() -> paymentRepository.save(new Payment(order, PaymentMethod.CASH_ON_DELIVERY, CASH_PROVIDER)));
        payment.markSucceeded("COD-" + order.getId());
        order.markPaid();
    }

    @Override
    @Transactional
    public void cancelPendingCashPayment(Order order) {
        paymentRepository.findFirstByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING)
                .ifPresent(payment -> payment.markCancelled("Order cancelled before delivery"));
    }

    /*
     * Same shape as pay(): read (own tx) -> gateway (no tx) -> write (own tx).
     * Runs after the cancellation has committed, so it must never throw: the cancel itself
     * already succeeded. A failed refund is logged; in production a scheduled job would
     * retry every order that is CANCELLED + PAID.
     */
    @Override
    public void refundIfPaid(Long orderId) {
        try {
            transactions.findRefundable(orderId).ifPresent(target -> {
                GatewayResult result = gateway.refund(target.transactionReference(), target.amount());
                if (result.successful()) {
                    transactions.recordRefund(target.paymentId(), result.reference());
                    log.info("Refunded payment {} of order {}", target.paymentId(), orderId);
                } else {
                    log.error("Refund of payment {} (order {}) was rejected: {}",
                            target.paymentId(), orderId, result.failureReason());
                }
            });
        } catch (RuntimeException e) {
            log.error("Refund for order {} failed; it must be retried", orderId, e);
        }
    }
}
