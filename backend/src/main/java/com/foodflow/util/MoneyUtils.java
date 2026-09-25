package com.foodflow.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one place money arithmetic is defined, so the cart (Phase 5) and order placement
 * (Phase 6) can never compute a total differently.
 * BigDecimal, never double: exact decimal arithmetic, rounded half-up to paise.
 */
public final class MoneyUtils {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private MoneyUtils() {
    }

    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return normalize(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    public static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
