package com.stockflow.sales.domain;

import com.stockflow.shared.domain.Money;
import java.math.BigDecimal;
import java.util.List;

public final class SaleCalculator {
    private SaleCalculator() {}

    public record LineAmount(BigDecimal subtotal, BigDecimal taxAmount) {}

    public record Totals(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total) {}

    public static LineAmount line(BigDecimal unitPrice, int quantity, BigDecimal taxRate) {
        var subtotal = Money.round(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        return new LineAmount(subtotal, Money.tax(subtotal, taxRate));
    }

    public static Totals total(List<LineAmount> lines) {
        var subtotal =
                lines.stream().map(LineAmount::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        var tax =
                lines.stream().map(LineAmount::taxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Totals(Money.round(subtotal), Money.round(tax), Money.round(subtotal.add(tax)));
    }
}
