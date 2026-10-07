package com.stockflow.businesses.application;

import com.stockflow.billing.domain.SubscriptionStatus;
import java.time.Instant;
import java.util.UUID;

public record BusinessView(
        UUID id,
        String name,
        String taxId,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        UUID planId,
        String planName,
        SubscriptionStatus subscriptionStatus,
        Instant trialEndsAt,
        Instant nextBillingDate) {}
