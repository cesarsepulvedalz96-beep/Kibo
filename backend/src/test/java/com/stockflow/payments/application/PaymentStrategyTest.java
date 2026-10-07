package com.stockflow.payments.application;

import static org.assertj.core.api.Assertions.*;

import com.stockflow.payments.domain.PaymentStrategy.PaymentRequest;
import com.stockflow.shared.domain.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentStrategyTest {
    @Test
    void cashRequiresSufficientTender() {
        assertThatThrownBy(
                        () ->
                                new CashPaymentStrategy()
                                        .record(
                                                new BigDecimal("100"),
                                                new PaymentRequest(
                                                        "CASH", new BigDecimal("99"), null, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("INSUFFICIENT_CASH");
    }

    @Test
    void externalPaymentMustBeExplicitlyConfirmedAndReferenced() {
        var strategy = new ExternalPaymentStrategy();
        assertThatThrownBy(
                        () ->
                                strategy.record(
                                        BigDecimal.TEN, new PaymentRequest("CARD", null, "", true)))
                .hasMessage("PAYMENT_CONFIRMATION_REQUIRED");
        assertThatThrownBy(
                        () ->
                                strategy.record(
                                        BigDecimal.TEN,
                                        new PaymentRequest("CARD", null, "REF-1", false)))
                .hasMessage("PAYMENT_CONFIRMATION_REQUIRED");
        assertThat(
                        strategy.record(
                                        BigDecimal.TEN,
                                        new PaymentRequest("CARD", null, "REF-1", true))
                                .mode())
                .isEqualTo("EXTERNAL_RECORDED");
    }
}
