package com.stockflow.realtime.application;

import java.time.Instant;
import java.util.UUID;

/** Minimal invalidation signal. Clients fetch authoritative state from the REST API. */
public record RealtimeMessage(String type, UUID resourceId, Instant occurredAt) {
    private static final String SUBSCRIPTION_CHANGED = "SUBSCRIPTION_CHANGED";

    public static RealtimeMessage now(String type, UUID resourceId) {
        return new RealtimeMessage(type, resourceId, Instant.now());
    }

    boolean isSubscriptionControl() {
        return SUBSCRIPTION_CHANGED.equals(type);
    }
}
