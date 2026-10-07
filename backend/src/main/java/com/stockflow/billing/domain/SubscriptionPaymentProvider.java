package com.stockflow.billing.domain;

/**
 * Adapter boundary for subscription billing. The MVP implementation only records manual payments.
 */
public interface SubscriptionPaymentProvider {
    String method();

    void validateRegistration(SubscriptionPayment payment);
}
