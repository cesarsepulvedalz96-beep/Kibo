package com.stockflow.payments.application;

import com.stockflow.payments.domain.PaymentStrategy;
import com.stockflow.shared.domain.BusinessException;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ExternalPaymentStrategy implements PaymentStrategy {
    public boolean supports(String method) {
        return Set.of("CARD", "TRANSFER", "EXTERNAL").contains(method);
    }

    public PaymentResult record(BigDecimal total, PaymentRequest request) {
        if (!request.externallyConfirmed()
                || request.reference() == null
                || request.reference().isBlank()) {
            throw new BusinessException("PAYMENT_CONFIRMATION_REQUIRED", 400);
        }
        return new PaymentResult(request.method(), "EXTERNAL_RECORDED", request.reference().trim());
    }
}
