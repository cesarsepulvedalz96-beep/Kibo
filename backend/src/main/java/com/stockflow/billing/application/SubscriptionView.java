package com.stockflow.billing.application;

import com.stockflow.billing.domain.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionView(
        UUID id,
        UUID businessId,
        String businessName,
        UUID planId,
        String planName,
        BigDecimal monthlyPrice,
        String currency,
        SubscriptionStatus status,
        Instant startedAt,
        Instant trialEndsAt,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant graceEndsAt,
        Instant nextBillingDate,
        Instant suspendedAt,
        Long remainingDays,
        Instant createdAt,
        Instant updatedAt) {}
