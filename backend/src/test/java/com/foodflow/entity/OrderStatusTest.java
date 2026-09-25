package com.foodflow.entity;

import com.foodflow.exception.InvalidOrderStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static com.foodflow.entity.OrderStatus.CANCELLED;
import static com.foodflow.entity.OrderStatus.CONFIRMED;
import static com.foodflow.entity.OrderStatus.DELIVERED;
import static com.foodflow.entity.OrderStatus.OUT_FOR_DELIVERY;
import static com.foodflow.entity.OrderStatus.PLACED;
import static com.foodflow.entity.OrderStatus.PREPARING;
import static com.foodflow.entity.OrderStatus.READY_FOR_PICKUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The state machine, exhaustively: every one of the 7 x 7 pairs is either legal or not. */
class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "PLACED, CONFIRMED",
            "PLACED, CANCELLED",
            "CONFIRMED, PREPARING",
            "PREPARING, READY_FOR_PICKUP",
            "READY_FOR_PICKUP, OUT_FOR_DELIVERY",
            "OUT_FOR_DELIVERY, DELIVERED"
    })
    void legalTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "DELIVERED, PREPARING",        // backwards
            "PLACED, DELIVERED",           // skipping steps
            "CONFIRMED, CANCELLED",        // too late to cancel
            "OUT_FOR_DELIVERY, CANCELLED",
            "CANCELLED, PLACED",           // resurrecting a cancelled order
            "PREPARING, PREPARING"         // no-op "transition"
    })
    void illegalTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void exactlySixLegalTransitionsExist() {
        long legal = 0;
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                if (from.canTransitionTo(to)) {
                    legal++;
                }
            }
        }
        assertThat(legal).isEqualTo(6);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED"})
    void terminalStatesAllowNothing(OrderStatus status) {
        assertThat(status.isTerminal()).isTrue();
        assertThat(status.allowedNext()).isEmpty();
    }

    @Test
    void orderEntityEnforcesTheMachineAndRecordsHistory() {
        Order order = new Order(null, null, null, "Home: 1 Road", PaymentMethod.CASH_ON_DELIVERY);

        order.changeStatus(CONFIRMED, null, null);
        order.changeStatus(PREPARING, null, null);

        assertThat(order.getStatus()).isEqualTo(PREPARING);
        assertThat(order.getStatusHistory()).extracting(OrderStatusHistory::getToStatus)
                .containsExactly(PLACED, CONFIRMED, PREPARING);

        assertThatThrownBy(() -> order.changeStatus(DELIVERED, null, null))
                .isInstanceOf(InvalidOrderStatusException.class)
                .hasMessage("Cannot change order status from PREPARING to DELIVERED. Allowed next: [READY_FOR_PICKUP]");
        assertThat(order.getStatus()).isEqualTo(PREPARING);         // unchanged
        assertThat(order.getStatusHistory()).hasSize(3);            // nothing recorded

        order.changeStatus(READY_FOR_PICKUP, null, null);
        order.changeStatus(OUT_FOR_DELIVERY, null, null);
        order.changeStatus(DELIVERED, null, null);
        assertThatThrownBy(() -> order.changeStatus(CANCELLED, null, "too late"))
                .isInstanceOf(InvalidOrderStatusException.class);
    }
}
