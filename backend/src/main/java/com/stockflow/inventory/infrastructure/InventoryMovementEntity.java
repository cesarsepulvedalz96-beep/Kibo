package com.stockflow.inventory.infrastructure;

import com.stockflow.inventory.domain.InventoryMovement;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "InventoryMovementEntity")
@Table(name = "inventory_movements")
public class InventoryMovementEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "type")
    private String type;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "previous_stock")
    private int previousStock;

    @Column(name = "new_stock")
    private int newStock;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "created_at")
    private Instant createdAt;

    protected InventoryMovementEntity() {}

    static InventoryMovementEntity fromDomain(InventoryMovement model) {
        var entity = new InventoryMovementEntity();
        entity.id = model.id();
        entity.businessId = model.businessId();
        entity.productId = model.productId();
        entity.type = model.type();
        entity.quantity = model.quantity();
        entity.previousStock = model.previousStock();
        entity.newStock = model.newStock();
        entity.referenceId = model.referenceId();
        entity.performedBy = model.performedBy();
        entity.createdAt = model.createdAt();
        return entity;
    }

    InventoryMovement toDomain() {
        return new InventoryMovement(
                id,
                businessId,
                productId,
                type,
                quantity,
                previousStock,
                newStock,
                referenceId,
                performedBy,
                createdAt);
    }
}
