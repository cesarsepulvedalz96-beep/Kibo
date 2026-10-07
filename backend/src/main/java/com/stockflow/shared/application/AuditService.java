package com.stockflow.shared.application;

import com.stockflow.shared.domain.AuditRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditRepository audit;

    public AuditService(AuditRepository audit) {
        this.audit = audit;
    }

    public void record(UUID businessId, UUID userId, String action, String entity, UUID entityId) {
        audit.record(businessId, userId, action, entity, entityId);
    }

    public void recordSystem(UUID businessId, String action, String entity, UUID entityId) {
        audit.recordSystem(businessId, action, entity, entityId);
    }
}
