package com.stockflow.inventory.infrastructure;

import com.stockflow.inventory.domain.InventoryOperationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInventoryOperationRepository implements InventoryOperationRepository {
    private final JdbcTemplate jdbc;

    public JdbcInventoryOperationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RecordedOperation> byRequest(UUID businessId, UUID requestId) {
        return jdbc
                .query(
                        "select id, request_fingerprint from inventory_operations where business_id = ? and request_id = ?",
                        (rs, row) ->
                                new RecordedOperation(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("request_fingerprint")),
                        businessId,
                        requestId)
                .stream()
                .findFirst();
    }

    @Override
    public void record(
            UUID id, UUID businessId, UUID requestId, String fingerprint, UUID performedBy) {
        jdbc.update(
                "insert into inventory_operations(id, business_id, request_id, request_fingerprint, performed_by) values (?, ?, ?, ?, ?)",
                id,
                businessId,
                requestId,
                fingerprint,
                performedBy);
    }
}
