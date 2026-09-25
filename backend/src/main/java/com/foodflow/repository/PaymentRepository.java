package com.foodflow.repository;

import com.foodflow.entity.Payment;
import com.foodflow.entity.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** All attempts for an order, oldest first: the order's money trail. */
    List<Payment> findByOrderIdOrderByIdAsc(Long orderId);

    Optional<Payment> findFirstByOrderIdAndStatus(Long orderId, PaymentStatus status);

    /** Payment + its order + the order's restaurant: enough for access checks and state updates. */
    @EntityGraph(attributePaths = {"order", "order.restaurant"})
    Optional<Payment> findWithOrderById(Long id);
}
