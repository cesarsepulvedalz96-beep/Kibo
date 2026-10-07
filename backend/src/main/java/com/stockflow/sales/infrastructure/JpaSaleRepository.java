package com.stockflow.sales.infrastructure;

import com.stockflow.sales.domain.*;
import com.stockflow.shared.infrastructure.JpaTenantRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSaleRepository extends JpaTenantRepository<Sale, SaleEntity>
        implements SaleRepository {
    public JpaSaleRepository(EntityManager entityManager) {
        super(entityManager, SaleEntity.class, SaleEntity::fromDomain, SaleEntity::toDomain);
    }

    public java.util.Optional<Sale> byRequest(java.util.UUID businessId, java.util.UUID requestId) {
        return entityManager
                .createQuery(
                        "select s from SaleEntity s where s.businessId = :business and s.requestId = :request",
                        SaleEntity.class)
                .setParameter("business", businessId)
                .setParameter("request", requestId)
                .getResultList()
                .stream()
                .findFirst()
                .map(toDomain);
    }

    public com.stockflow.shared.domain.Page<Sale> history(
            java.util.UUID businessId, int page, int size) {
        var items =
                entityManager
                        .createQuery(
                                "select s from SaleEntity s where s.businessId = :business order by s.createdAt desc, s.id",
                                SaleEntity.class)
                        .setParameter("business", businessId)
                        .setFirstResult(page * size)
                        .setMaxResults(size)
                        .getResultList()
                        .stream()
                        .map(toDomain)
                        .toList();
        long count =
                entityManager
                        .createQuery(
                                "select count(s) from SaleEntity s where s.businessId = :business",
                                Long.class)
                        .setParameter("business", businessId)
                        .getSingleResult();
        return new com.stockflow.shared.domain.Page<>(items, count, page, size);
    }
}
