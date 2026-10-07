package com.stockflow.billing.infrastructure;

import com.stockflow.billing.domain.*;
import com.stockflow.shared.domain.Page;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSubscriptionPaymentRepository implements SubscriptionPaymentRepository {
    private final JdbcTemplate jdbc;

    public JdbcSubscriptionPaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SubscriptionPayment> find(UUID id) {
        return jdbc
                .query("select * from subscription_payments where id = ?", this::map, id)
                .stream()
                .findFirst();
    }

    public Optional<SubscriptionPayment> lock(UUID id) {
        return jdbc
                .query("select * from subscription_payments where id = ? for update", this::map, id)
                .stream()
                .findFirst();
    }

    public Optional<SubscriptionPayment> findByRequest(UUID businessId, UUID requestId) {
        return jdbc
                .query(
                        "select * from subscription_payments where business_id=? and request_id=?",
                        this::map,
                        businessId,
                        requestId)
                .stream()
                .findFirst();
    }

    public Page<SubscriptionPayment> list(
            UUID businessId, PaymentStatus status, int page, int size) {
        String statusName = status == null ? null : status.name();
        String where =
                " where (cast(? as uuid) is null or business_id=?)"
                        + " and (cast(? as varchar) is null or status=?)";
        var items =
                jdbc.query(
                        "select * from subscription_payments"
                                + where
                                + " order by created_at desc,id limit ? offset ?",
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
                                "select count(*) from subscription_payments" + where,
                                Long.class,
                                businessId,
                                businessId,
                                statusName,
                                statusName));
        return new Page<>(items, total, page, size);
    }

    public SubscriptionPayment save(SubscriptionPayment p) {
        jdbc.update(
                "insert into subscription_payments(id,subscription_id,business_id,request_id,amount,currency,payment_method,status,external_reference,paid_at,created_at,updated_at)"
                        + " values (?,?,?,?,?,?,?,?,?,?,?,?) on conflict(id) do update set status=excluded.status,external_reference=excluded.external_reference,paid_at=excluded.paid_at,updated_at=excluded.updated_at",
                p.id(),
                p.subscriptionId(),
                p.businessId(),
                p.requestId(),
                p.amount(),
                p.currency(),
                p.paymentMethod(),
                p.status().name(),
                p.externalReference(),
                timestamp(p.paidAt()),
                timestamp(p.createdAt()),
                timestamp(p.updatedAt()));
        return find(p.id()).orElseThrow();
    }

    private SubscriptionPayment map(ResultSet rs, int ignored) throws SQLException {
        return new SubscriptionPayment(
                rs.getObject("id", UUID.class),
                rs.getObject("subscription_id", UUID.class),
                rs.getObject("business_id", UUID.class),
                rs.getObject("request_id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("payment_method"),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getString("external_reference"),
                instant(rs, "paid_at"),
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
