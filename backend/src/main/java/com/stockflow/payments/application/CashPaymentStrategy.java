package com.stockflow.payments.application;

import com.stockflow.payments.domain.PaymentStrategy;
import com.stockflow.shared.domain.BusinessException;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class CashPaymentStrategy implements PaymentStrategy {
    public boolean supports(String method) {
        return method.equals("CASH");
    }

    public PaymentResult record(BigDecimal total, PaymentRequest request) {
        if (request.cashReceived() == null || request.cashReceived().compareTo(total) < 0) {
            throw new BusinessException("INSUFFICIENT_CASH", 400);
        }
        return new PaymentResult("CASH", "MANUAL", null);
    }
}
