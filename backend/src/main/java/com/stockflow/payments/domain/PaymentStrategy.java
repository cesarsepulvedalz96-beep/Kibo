package com.stockflow.payments.domain;

import java.math.BigDecimal;

public interface PaymentStrategy {
    boolean supports(String method);

    PaymentResult record(BigDecimal total, PaymentRequest request);

    record PaymentRequest(
            String method,
            BigDecimal cashReceived,
            String reference,
            boolean externallyConfirmed) {}

    record PaymentResult(String method, String mode, String reference) {}
}
