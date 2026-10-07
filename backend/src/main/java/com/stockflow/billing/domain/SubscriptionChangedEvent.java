package com.stockflow.billing.domain;

import java.util.UUID;

public record SubscriptionChangedEvent(UUID businessId, UUID subscriptionId, String status) {}
