package com.stockflow.businesses.infrastructure;

import com.stockflow.businesses.domain.Business;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "businesses")
public class BusinessEntity {
    @Id private UUID id;
    private String name;
    private String taxId;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    protected BusinessEntity() {}

    static BusinessEntity fromDomain(Business business) {
        var entity = new BusinessEntity();
        entity.id = business.id();
        entity.name = business.name();
        entity.taxId = business.taxId();
        entity.active = business.active();
        entity.createdAt = business.createdAt();
        entity.updatedAt = business.updatedAt();
        return entity;
    }

    Business toDomain() {
        return new Business(id, name, taxId, active, createdAt, updatedAt);
    }
}
