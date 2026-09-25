package com.foodflow.service.payment;

/** Outcome of a charge or refund: success with the gateway's reference, or a failure reason. */
public record GatewayResult(boolean successful, String reference, String failureReason) {

    public static GatewayResult success(String reference) {
        return new GatewayResult(true, reference, null);
    }

    public static GatewayResult failure(String reason) {
        return new GatewayResult(false, null, reason);
    }
}
