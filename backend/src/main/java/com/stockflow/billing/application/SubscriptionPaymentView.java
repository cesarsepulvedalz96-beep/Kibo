package com.stockflow.billing.application;

import com.stockflow.billing.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionPaymentView(
        UUID id,
        UUID subscriptionId,
        UUID businessId,
        String businessName,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        PaymentStatus status,
        String externalReference,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt) {}
