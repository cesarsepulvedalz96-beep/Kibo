package com.stockflow.shared.infrastructure;

import com.stockflow.shared.domain.AuditRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditRepository implements AuditRepository {
    private final JdbcTemplate jdbc;

    public JdbcAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UUID businessId, UUID userId, String action, String entity, UUID entityId) {
        jdbc.update(
                "insert into audit_events(id, business_id, user_id, action, entity, entity_id, actor_type) values (?, ?, ?, ?, ?, ?, 'USER')",
                UUID.randomUUID(),
                businessId,
                userId,
                action,
                entity,
                entityId);
    }

    @Override
    public void recordSystem(UUID businessId, String action, String entity, UUID entityId) {
        jdbc.update(
                "insert into audit_events(id, business_id, user_id, action, entity, entity_id, actor_type) values (?, ?, null, ?, ?, ?, 'SYSTEM')",
                UUID.randomUUID(),
                businessId,
                action,
                entity,
                entityId);
    }
}
