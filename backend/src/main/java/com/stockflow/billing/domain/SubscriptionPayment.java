package com.stockflow.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionPayment(
        UUID id,
        UUID subscriptionId,
        UUID businessId,
        UUID requestId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        PaymentStatus status,
        String externalReference,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt) {}
