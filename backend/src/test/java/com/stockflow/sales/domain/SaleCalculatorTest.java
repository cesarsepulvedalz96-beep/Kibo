package com.stockflow.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaleCalculatorTest {
    @Test
    void calculatesTaxWithExplicitHalfUpRoundingPerLine() {
        var line = SaleCalculator.line(new BigDecimal("10.50"), 3, new BigDecimal("0.19"));
        assertThat(line.subtotal()).isEqualByComparingTo("31.50");
        assertThat(line.taxAmount()).isEqualByComparingTo("5.99");
        assertThat(SaleCalculator.total(List.of(line)).total()).isEqualByComparingTo("37.49");
    }

    @Test
    void totalsSumRoundedLinesInsteadOfRecalculatingTaxOverTheWholeSale() {
        var line = SaleCalculator.line(new BigDecimal("0.03"), 1, new BigDecimal("0.19"));
        var totals = SaleCalculator.total(List.of(line, line));
        assertThat(totals.subtotal()).isEqualByComparingTo("0.06");
        assertThat(totals.taxAmount()).isEqualByComparingTo("0.02");
        assertThat(totals.total()).isEqualByComparingTo("0.08");
    }

    @Test
    void disabledTaxHasNoHiddenCharge() {
        var line = SaleCalculator.line(new BigDecimal("1290"), 2, BigDecimal.ZERO);
        assertThat(line.taxAmount()).isEqualByComparingTo("0");
        assertThat(SaleCalculator.total(List.of(line)).total()).isEqualByComparingTo("2580");
    }
}
