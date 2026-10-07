package com.stockflow.billing.domain;

import java.time.Instant;
import java.util.UUID;

public record Subscription(
        UUID id,
        UUID businessId,
        UUID planId,
        SubscriptionStatus status,
        Instant startedAt,
        Instant trialEndsAt,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant graceEndsAt,
        Instant nextBillingDate,
        Instant suspendedAt,
        Instant createdAt,
        Instant updatedAt) {}
