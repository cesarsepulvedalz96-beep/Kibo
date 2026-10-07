package com.stockflow.notifications.infrastructure;

import com.stockflow.notifications.domain.Notification;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "NotificationEntity")
@Table(name = "notifications")
public class NotificationEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "stock")
    private int stock;

    @Column(name = "read")
    private boolean read;

    @Column(name = "created_at")
    private Instant createdAt;

    protected NotificationEntity() {}

    static NotificationEntity fromDomain(Notification model) {
        var entity = new NotificationEntity();
        entity.id = model.id();
        entity.businessId = model.businessId();
        entity.productId = model.productId();
        entity.productName = model.productName();
        entity.stock = model.stock();
        entity.read = model.read();
        entity.createdAt = model.createdAt();
        return entity;
    }

    Notification toDomain() {
        return new Notification(id, businessId, productId, productName, stock, read, createdAt);
    }
}
