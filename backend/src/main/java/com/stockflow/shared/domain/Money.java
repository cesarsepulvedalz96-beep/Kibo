package com.stockflow.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Net prices. Tax is rounded per line; totals are sums of the rounded lines. */
public final class Money {
    private Money() {}

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal tax(BigDecimal net, BigDecimal rate) {
        return round(net.multiply(rate));
    }
}
