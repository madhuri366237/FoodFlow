package com.foodflow.dto.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One bar of the "last 7 days" chart; days without orders are included with zeros. */
public record DailyPoint(LocalDate date, long orders, BigDecimal revenue) {
}
