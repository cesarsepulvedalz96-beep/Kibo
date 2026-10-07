package com.stockflow.inventory.infrastructure;

import com.stockflow.inventory.domain.ManualMovementRequestRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcManualMovementRequestRepository implements ManualMovementRequestRepository {
    private final JdbcTemplate jdbc;

    public JdbcManualMovementRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RecordedRequest> find(UUID businessId, UUID requestId) {
        return jdbc
                .query(
                        "select movement_id, request_fingerprint from manual_inventory_requests where business_id = ? and request_id = ?",
                        (rs, row) ->
                                new RecordedRequest(
                                        rs.getObject("movement_id", UUID.class),
                                        rs.getString("request_fingerprint")),
                        businessId,
                        requestId)
                .stream()
                .findFirst();
    }

    @Override
    public void record(UUID businessId, UUID requestId, String fingerprint, UUID movementId) {
        jdbc.update(
                "insert into manual_inventory_requests(business_id, request_id, request_fingerprint, movement_id) values (?, ?, ?, ?)",
                businessId,
                requestId,
                fingerprint,
                movementId);
    }
}
