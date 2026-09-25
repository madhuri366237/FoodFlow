package com.foodflow.service.payment;

import com.foodflow.entity.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedPaymentGatewayTest {

    private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway(0.0);

    private static GatewayChargeRequest charge(String token) {
        return new GatewayChargeRequest("payment-1", new BigDecimal("498.00"), "INR", PaymentMethod.CARD, token);
    }

    @ParameterizedTest
    @CsvSource({"tok_visa", "upi_success", "anything-else"})
    void ordinaryTokensSucceedWithAReference(String token) {
        GatewayResult result = gateway.charge(charge(token));

        assertThat(result.successful()).isTrue();
        assertThat(result.reference()).startsWith("SIM_TXN_");
    }

    @ParameterizedTest
    @CsvSource({"tok_chargeDeclined, Card declined", "tok_insufficientFunds, Insufficient funds"})
    void magicTokensFailWithAReason(String token, String reason) {
        GatewayResult result = gateway.charge(charge(token));

        assertThat(result.successful()).isFalse();
        assertThat(result.failureReason()).isEqualTo(reason);
    }

    @Test
    void timeoutTokenSimulatesAnUnreachableGateway() {
        assertThatThrownBy(() -> gateway.charge(charge("tok_timeout"))).isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void failureRateOfOneFailsEveryOtherwiseSuccessfulCharge() {
        SimulatedPaymentGateway alwaysFails = new SimulatedPaymentGateway(1.0);

        assertThat(alwaysFails.charge(charge("tok_visa")).successful()).isFalse();
    }

    @Test
    void invalidFailureRateIsRejectedAtStartup() {
        assertThatThrownBy(() -> new SimulatedPaymentGateway(1.5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refundSucceeds() {
        assertThat(gateway.refund("SIM_TXN_abc", new BigDecimal("498.00")).reference()).startsWith("SIM_RFND_");
    }
}
