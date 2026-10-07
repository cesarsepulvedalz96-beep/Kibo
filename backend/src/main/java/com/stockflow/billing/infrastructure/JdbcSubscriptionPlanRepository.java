package com.stockflow.billing.infrastructure;

import com.stockflow.billing.domain.*;
import com.stockflow.shared.domain.Page;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSubscriptionPlanRepository implements SubscriptionPlanRepository {
    private final JdbcTemplate jdbc;

    public JdbcSubscriptionPlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SubscriptionPlan> find(UUID id) {
        return jdbc.query("select * from subscription_plans where id = ?", this::map, id).stream()
                .findFirst();
    }

    public Optional<SubscriptionPlan> findDefaultActive() {
        return jdbc
                .query(
                        "select * from subscription_plans where active order by created_at, id limit 1",
                        this::map)
                .stream()
                .findFirst();
    }

    public Page<SubscriptionPlan> list(Boolean active, String search, int page, int size) {
        String normalized =
                search == null || search.isBlank()
                        ? null
                        : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        String where =
                " where (cast(? as boolean) is null or active = ?)"
                        + " and (cast(? as varchar) is null or lower(name) like ?)";
        var items =
                jdbc.query(
                        "select * from subscription_plans"
                                + where
                                + " order by created_at desc, id limit ? offset ?",
                        this::map,
                        active,
                        active,
                        normalized,
                        normalized,
                        size,
                        page * size);
        long total =
                Objects.requireNonNull(
                        jdbc.queryForObject(
                                "select count(*) from subscription_plans" + where,
                                Long.class,
                                active,
                                active,
                                normalized,
                                normalized));
        return new Page<>(items, total, page, size);
    }

    public SubscriptionPlan save(SubscriptionPlan p) {
        jdbc.update(
                "insert into subscription_plans(id,name,description,monthly_price,billing_period_days,grace_period_days,active,created_at,updated_at)"
                        + " values (?,?,?,?,?,?,?,?,?) on conflict(id) do update set name=excluded.name,description=excluded.description,monthly_price=excluded.monthly_price,billing_period_days=excluded.billing_period_days,grace_period_days=excluded.grace_period_days,active=excluded.active,updated_at=excluded.updated_at",
                p.id(),
                p.name(),
                p.description(),
                p.monthlyPrice(),
                p.billingPeriodDays(),
                p.gracePeriodDays(),
                p.active(),
                Timestamp.from(p.createdAt()),
                Timestamp.from(p.updatedAt()));
        return find(p.id()).orElseThrow();
    }

    private SubscriptionPlan map(ResultSet rs, int ignored) throws SQLException {
        return new SubscriptionPlan(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("monthly_price"),
                rs.getInt("billing_period_days"),
                rs.getInt("grace_period_days"),
                rs.getBoolean("active"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
