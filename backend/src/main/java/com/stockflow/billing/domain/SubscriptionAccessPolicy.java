package com.stockflow.billing.domain;

import java.util.UUID;

public interface SubscriptionAccessPolicy {
    boolean hasCommercialAccess(UUID businessId);

    void requireCommercialAccess(UUID businessId);
}
