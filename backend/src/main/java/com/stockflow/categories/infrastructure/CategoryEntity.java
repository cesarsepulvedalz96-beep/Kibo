package com.stockflow.categories.infrastructure;

import com.stockflow.categories.domain.Category;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "CategoryEntity")
@Table(name = "categories")
public class CategoryEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "name")
    private String name;

    @Column(name = "active")
    private boolean active;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected CategoryEntity() {}

    static CategoryEntity fromDomain(Category model) {
        var entity = new CategoryEntity();
        entity.id = model.id();
        entity.businessId = model.businessId();
        entity.name = model.name();
        entity.active = model.active();
        entity.createdAt = model.createdAt();
        entity.updatedAt = model.updatedAt();
        return entity;
    }

    Category toDomain() {
        return new Category(id, businessId, name, active, createdAt, updatedAt);
    }
}
