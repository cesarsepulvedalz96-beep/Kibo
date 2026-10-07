package com.stockflow.billing.infrastructure;

import com.stockflow.billing.domain.*;
import com.stockflow.shared.domain.Page;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSubscriptionRepository implements SubscriptionRepository {
    private final JdbcTemplate jdbc;

    public JdbcSubscriptionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Subscription> find(UUID id) {
        return jdbc.query("select * from subscriptions where id = ?", this::map, id).stream()
                .findFirst();
    }

    public Optional<Subscription> findByBusiness(UUID businessId) {
        return jdbc
                .query("select * from subscriptions where business_id = ?", this::map, businessId)
                .stream()
                .findFirst();
    }

    public Optional<Subscription> lockByBusiness(UUID businessId) {
        return jdbc
                .query(
                        "select * from subscriptions where business_id = ? for update",
                        this::map,
                        businessId)
                .stream()
                .findFirst();
    }

    public Page<Subscription> list(UUID businessId, SubscriptionStatus status, int page, int size) {
        String where =
                " where (cast(? as uuid) is null or business_id = ?)"
                        + " and (cast(? as varchar) is null or status = ?)";
        String statusName = status == null ? null : status.name();
        var items =
                jdbc.query(
                        "select * from subscriptions"
                                + where
                                + " order by created_at desc, id limit ? offset ?",
                        this::map,
                        businessId,
                        businessId,
                        statusName,
                        statusName,
                        size,
                        page * size);
        long total =
                Objects.requireNonNull(
                        jdbc.queryForObject(
                                "select count(*) from subscriptions" + where,
                                Long.class,
                                businessId,
                                businessId,
                                statusName,
                                statusName));
        return new Page<>(items, total, page, size);
    }

    public List<UUID> findBusinessesDue(Instant now, int limit) {
        return jdbc.queryForList(
                "select business_id from subscriptions where"
                        + " (status='TRIAL' and trial_ends_at <= ?)"
                        + " or (status='ACTIVE' and current_period_end <= ?)"
                        + " or (status='GRACE_PERIOD' and grace_ends_at <= ?)"
                        + " order by updated_at limit ?",
                UUID.class,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now),
                limit);
    }

    public Subscription save(Subscription s) {
        jdbc.update(
                "insert into subscriptions(id,business_id,plan_id,status,started_at,trial_ends_at,current_period_start,current_period_end,grace_ends_at,next_billing_date,suspended_at,created_at,updated_at)"
                        + " values (?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(id) do update set plan_id=excluded.plan_id,status=excluded.status,trial_ends_at=excluded.trial_ends_at,current_period_start=excluded.current_period_start,current_period_end=excluded.current_period_end,grace_ends_at=excluded.grace_ends_at,next_billing_date=excluded.next_billing_date,suspended_at=excluded.suspended_at,updated_at=excluded.updated_at",
                s.id(),
                s.businessId(),
                s.planId(),
                s.status().name(),
                timestamp(s.startedAt()),
                timestamp(s.trialEndsAt()),
                timestamp(s.currentPeriodStart()),
                timestamp(s.currentPeriodEnd()),
                timestamp(s.graceEndsAt()),
                timestamp(s.nextBillingDate()),
                timestamp(s.suspendedAt()),
                timestamp(s.createdAt()),
                timestamp(s.updatedAt()));
        return find(s.id()).orElseThrow();
    }

    private Subscription map(ResultSet rs, int ignored) throws SQLException {
        return new Subscription(
                rs.getObject("id", UUID.class),
                rs.getObject("business_id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                SubscriptionStatus.valueOf(rs.getString("status")),
                instant(rs, "started_at"),
                instant(rs, "trial_ends_at"),
                instant(rs, "current_period_start"),
                instant(rs, "current_period_end"),
                instant(rs, "grace_ends_at"),
                instant(rs, "next_billing_date"),
                instant(rs, "suspended_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
