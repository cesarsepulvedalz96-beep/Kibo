package com.stockflow.billing.infrastructure;

import com.stockflow.billing.domain.*;
import com.stockflow.shared.domain.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class ManualSubscriptionPaymentProvider implements SubscriptionPaymentProvider {
    public String method() {
        return "MANUAL";
    }

    public void validateRegistration(SubscriptionPayment payment) {
        if (payment.externalReference() != null && payment.externalReference().length() > 160) {
            throw new BusinessException("VALIDATION_ERROR", 400);
        }
    }
}
