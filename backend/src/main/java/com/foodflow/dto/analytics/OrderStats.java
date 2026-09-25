package com.foodflow.dto.analytics;

import java.math.BigDecimal;

/**
 * Order figures for one scope (whole platform, one owner, or one customer).
 *
 * <p>Revenue = total of orders whose payment_status is PAID: money actually received.
 * Online orders count once paid; cash orders once delivered; refunded or unpaid orders never.
 */
public record OrderStats(
        long totalOrders,
        long todayOrders,
        long activeOrders,        // not yet DELIVERED or CANCELLED
        long deliveredOrders,
        long cancelledOrders,
        long paidOrders,
        BigDecimal revenue,
        BigDecimal todayRevenue,
        BigDecimal averageOrderValue,
        BigDecimal discountsGiven) {
}
