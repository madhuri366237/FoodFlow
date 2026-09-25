package com.foodflow.dto.analytics;

import java.math.BigDecimal;

/** Grouped by the SNAPSHOT name from order_items, so dishes deleted since still count. */
public record TopDish(String name, long quantity, BigDecimal revenue) {
}
