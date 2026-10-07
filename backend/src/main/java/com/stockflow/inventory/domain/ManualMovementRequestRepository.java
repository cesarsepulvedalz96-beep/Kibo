package com.stockflow.inventory.domain;

import java.util.Optional;
import java.util.UUID;

/** Request keys are scoped to the manual inventory operation and authenticated business. */
public interface ManualMovementRequestRepository {
    record RecordedRequest(UUID movementId, String fingerprint) {}

    Optional<RecordedRequest> find(UUID businessId, UUID requestId);

    void record(UUID businessId, UUID requestId, String fingerprint, UUID movementId);
}
