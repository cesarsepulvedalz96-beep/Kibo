package com.stockflow.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionPlan(
        UUID id,
        String name,
        String description,
        BigDecimal monthlyPrice,
        int billingPeriodDays,
        int gracePeriodDays,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}
