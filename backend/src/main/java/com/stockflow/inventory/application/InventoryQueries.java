package com.stockflow.inventory.application;

import com.stockflow.inventory.domain.MovementType;
import com.stockflow.shared.domain.Page;
import java.time.Instant;
import java.util.UUID;

public interface InventoryQueries {
    record MovementView(
            UUID id,
            UUID productId,
            String productName,
            String type,
            int quantity,
            int previousStock,
            int newStock,
            UUID referenceId,
            String performedBy,
            Instant createdAt) {}

    Page<MovementView> list(UUID businessId, UUID productId, MovementType type, int page, int size);
}
