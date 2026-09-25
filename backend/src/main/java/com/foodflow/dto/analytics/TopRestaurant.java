package com.foodflow.dto.analytics;

import java.math.BigDecimal;

public record TopRestaurant(Long id, String name, long orders, BigDecimal revenue) {
}
