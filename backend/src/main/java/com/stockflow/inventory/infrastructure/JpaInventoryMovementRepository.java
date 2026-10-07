package com.stockflow.inventory.infrastructure;

import com.stockflow.inventory.domain.*;
import com.stockflow.shared.infrastructure.JpaTenantRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class JpaInventoryMovementRepository
        extends JpaTenantRepository<InventoryMovement, InventoryMovementEntity>
        implements InventoryMovementRepository {
    public JpaInventoryMovementRepository(EntityManager entityManager) {
        super(
                entityManager,
                InventoryMovementEntity.class,
                InventoryMovementEntity::fromDomain,
                InventoryMovementEntity::toDomain);
    }
}
