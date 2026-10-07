package com.stockflow.notifications.domain;

import com.stockflow.shared.domain.TenantOwned;
import java.time.Instant;
import java.util.UUID;

public record Notification(
        UUID id,
        UUID businessId,
        UUID productId,
        String productName,
        int stock,
        boolean read,
        Instant createdAt)
        implements TenantOwned {}
