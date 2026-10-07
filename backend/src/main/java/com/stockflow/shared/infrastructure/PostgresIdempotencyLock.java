package com.stockflow.shared.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class PostgresIdempotencyLock implements com.stockflow.shared.domain.IdempotencyLock {
    private final JdbcTemplate jdbc;

    public PostgresIdempotencyLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(UUID businessId, UUID requestId) {
        jdbc.query(
                "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> {},
                businessId + ":" + requestId);
    }
}
