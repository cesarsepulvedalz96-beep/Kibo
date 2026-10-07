package com.stockflow.sales.infrastructure;

import com.stockflow.sales.domain.*;
import com.stockflow.shared.infrastructure.JpaTenantRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSaleItemRepository extends JpaTenantRepository<SaleItem, SaleItemEntity>
        implements SaleItemRepository {
    public JpaSaleItemRepository(EntityManager entityManager) {
        super(
                entityManager,
                SaleItemEntity.class,
                SaleItemEntity::fromDomain,
                SaleItemEntity::toDomain);
    }

    public java.util.List<SaleItem> forSale(java.util.UUID businessId, java.util.UUID saleId) {
        return entityManager
                .createQuery(
                        "select i from SaleItemEntity i where i.businessId = :business and i.saleId = :sale order by i.productNameSnapshot",
                        SaleItemEntity.class)
                .setParameter("business", businessId)
                .setParameter("sale", saleId)
                .getResultList()
                .stream()
                .map(toDomain)
                .toList();
    }
}
