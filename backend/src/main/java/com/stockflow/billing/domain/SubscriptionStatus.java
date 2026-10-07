package com.stockflow.billing.domain;

public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    GRACE_PERIOD,
    SUSPENDED,
    CANCELLED;

    public boolean permitsCommercialAccess() {
        return this == TRIAL || this == ACTIVE || this == GRACE_PERIOD;
    }
}
