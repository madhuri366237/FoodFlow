package com.foodflow.service.impl;

import com.foodflow.dto.payment.PaymentResponse;
import com.foodflow.entity.Order;
import com.foodflow.entity.OrderPaymentStatus;
import com.foodflow.entity.OrderStatus;
import com.foodflow.entity.Payment;
import com.foodflow.entity.PaymentMethod;
import com.foodflow.entity.PaymentStatus;
import com.foodflow.exception.ConflictException;
import com.foodflow.exception.ResourceNotFoundException;
import com.foodflow.repository.OrderRepository;
import com.foodflow.repository.PaymentRepository;
import com.foodflow.service.payment.GatewayResult;
import com.foodflow.service.payment.PaymentGateway;
import com.foodflow.service.payment.RefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The short database transactions around a gateway call.
 *
 * <p>Why a SEPARATE bean instead of private methods in PaymentServiceImpl? {@code @Transactional}
 * works through a Spring proxy that wraps the bean. A call from one method to another method of
 * the SAME object never passes through that proxy, so the annotation would be silently ignored
 * ("self-invocation"). Calling these methods on another bean goes through its proxy, so each one
 * really gets its own transaction.
 */
@Component
@RequiredArgsConstructor
class PaymentTransactions {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Value("${app.payment.pending-timeout-minutes:10}")
    private long pendingTimeoutMinutes;

    /** What the gateway call needs, handed out of the first transaction (no managed entities). */
    record StartedPayment(Long paymentId, BigDecimal amount, PaymentMethod method) {
    }

    record RefundTarget(Long paymentId, String transactionReference, BigDecimal amount) {
    }

    /**
     * Transaction 1: validate, then record a PENDING attempt and COMMIT it before any money moves.
     * If the server dies during the gateway call, this row is the evidence that a charge may have
     * been attempted, which is what reconciliation needs.
     */
    @Transactional
    public StartedPayment start(Long customerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));

        if (!order.getPaymentMethod().isOnline()) {
            throw new ConflictException("PAYMENT_NOT_REQUIRED", "Cash on delivery orders are paid to the rider on delivery");
        }
        if (order.getPaymentStatus() != OrderPaymentStatus.PENDING) {
            throw new ConflictException("ALREADY_PAID", "Order " + orderId + " is already " + order.getPaymentStatus());
        }
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new ConflictException("ORDER_NOT_PAYABLE", "Order " + orderId + " is " + order.getStatus());
        }

        Optional<Payment> pending = paymentRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.PENDING);
        if (pending.isPresent()) {
            if (pending.get().getCreatedAt().isAfter(Instant.now(clock).minus(Duration.ofMinutes(pendingTimeoutMinutes)))) {
                throw new ConflictException("PAYMENT_IN_PROGRESS", "A payment for this order is already in progress");
            }
            // Abandoned attempt (no result was ever recorded): close it so the customer can retry.
            // A production system would first ask the gateway what happened to it (reconciliation).
            pending.get().markFailed("Abandoned: no result recorded within " + pendingTimeoutMinutes + " minutes");
            paymentRepository.flush();
        }

        Payment payment = new Payment(order, order.getPaymentMethod(), gateway.name());
        try {
            // The partial unique index uk_payments_one_active_per_order makes this INSERT fail
            // if a concurrent request inserted its PENDING row first: no double charge.
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("PAYMENT_IN_PROGRESS", "A payment for this order is already in progress");
        }
        return new StartedPayment(payment.getId(), payment.getAmount(), payment.getMethod());
    }

    /** Transaction 2: record what the gateway said. */
    @Transactional
    public PaymentResponse complete(Long paymentId, GatewayResult result) {
        Payment payment = paymentRepository.findWithOrderById(paymentId).orElseThrow();
        Order order = payment.getOrder();

        if (result.successful()) {
            payment.markSucceeded(result.reference());
            if (order.getStatus() == OrderStatus.CANCELLED) {
                // The customer cancelled while the charge was in flight: we now hold money for a
                // cancelled order, so give it back (compensation, handled after this commit).
                events.publishEvent(new RefundRequestedEvent(order.getId()));
            } else {
                order.markPaid();
            }
        } else {
            payment.markFailed(result.failureReason());
        }
        paymentRepository.flush();
        return PaymentResponse.from(payment);
    }

    /*
     * REQUIRES_NEW: these run from an AFTER_COMMIT event listener. At that point the original
     * transaction has committed but is still bound to the thread; joining it (REQUIRED) would
     * mean our writes are never committed. REQUIRES_NEW always starts a fresh transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<RefundTarget> findRefundable(Long orderId) {
        return paymentRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)
                .filter(payment -> payment.getMethod().isOnline())
                .map(payment -> new RefundTarget(payment.getId(), payment.getTransactionReference(), payment.getAmount()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRefund(Long paymentId, String refundReference) {
        Payment payment = paymentRepository.findWithOrderById(paymentId).orElseThrow();
        payment.markRefunded(refundReference);
        payment.getOrder().markRefunded();
    }
}
