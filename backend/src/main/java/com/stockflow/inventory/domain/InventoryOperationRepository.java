package com.stockflow.inventory.domain;

import java.util.Optional;
import java.util.UUID;

public interface InventoryOperationRepository {
    record RecordedOperation(UUID id, String requestFingerprint) {}

    Optional<RecordedOperation> byRequest(UUID businessId, UUID requestId);

    void record(UUID id, UUID businessId, UUID requestId, String fingerprint, UUID performedBy);
}
