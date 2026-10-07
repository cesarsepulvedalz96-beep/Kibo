package com.stockflow.inventory.domain;

import com.stockflow.shared.domain.TenantOwned;
import java.time.Instant;
import java.util.UUID;

public record InventoryMovement(
        UUID id,
        UUID businessId,
        UUID productId,
        String type,
        int quantity,
        int previousStock,
        int newStock,
        UUID referenceId,
        UUID performedBy,
        Instant createdAt)
        implements TenantOwned {}
