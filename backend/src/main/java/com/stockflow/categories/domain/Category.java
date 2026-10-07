package com.stockflow.categories.domain;

import com.stockflow.shared.domain.TenantOwned;
import java.time.Instant;
import java.util.UUID;

public record Category(
        UUID id, UUID businessId, String name, boolean active, Instant createdAt, Instant updatedAt)
        implements TenantOwned {}
