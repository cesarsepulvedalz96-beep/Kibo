package com.stockflow.shared.domain;

import java.util.UUID;

public interface AuditRepository {
    void record(UUID businessId, UUID userId, String action, String entity, UUID entityId);

    void recordSystem(UUID businessId, String action, String entity, UUID entityId);
}
