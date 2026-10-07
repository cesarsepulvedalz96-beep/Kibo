package com.stockflow.sales.infrastructure;

import com.stockflow.sales.domain.Sale;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "SaleEntity")
@Table(name = "sales")
public class SaleEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "subtotal")
    private BigDecimal subtotal;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    @Column(name = "total")
    private BigDecimal total;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "payment_mode")
    private String paymentMode;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "status")
    private String status;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "request_fingerprint")
    private String requestFingerprint;

    @Column(name = "created_at")
    private Instant createdAt;

    protected SaleEntity() {}

    static SaleEntity fromDomain(Sale model) {
        var entity = new SaleEntity();
        entity.id = model.id();
        entity.businessId = model.businessId();
        entity.userId = model.userId();
        entity.subtotal = model.subtotal();
        entity.taxAmount = model.taxAmount();
        entity.total = model.total();
        entity.paymentMethod = model.paymentMethod();
        entity.paymentMode = model.paymentMode();
        entity.paymentReference = model.paymentReference();
        entity.status = model.status();
        entity.requestId = model.requestId();
        entity.requestFingerprint = model.requestFingerprint();
        entity.createdAt = model.createdAt();
        return entity;
    }

    Sale toDomain() {
        return new Sale(
                id,
                businessId,
                userId,
                subtotal,
                taxAmount,
                total,
                paymentMethod,
                paymentMode,
                paymentReference,
                status,
                requestId,
                requestFingerprint,
                createdAt);
    }
}
