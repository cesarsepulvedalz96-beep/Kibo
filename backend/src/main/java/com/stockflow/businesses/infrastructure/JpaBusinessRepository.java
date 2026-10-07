package com.stockflow.businesses.infrastructure;

import com.stockflow.businesses.domain.*;
import com.stockflow.shared.domain.Page;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaBusinessRepository implements BusinessRepository {
    private final EntityManager entityManager;

    public JpaBusinessRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<Business> find(UUID id) {
        return Optional.ofNullable(entityManager.find(BusinessEntity.class, id))
                .map(BusinessEntity::toDomain);
    }

    public Page<Business> list(int page, int size) {
        var items =
                entityManager
                        .createQuery(
                                "select b from BusinessEntity b order by b.createdAt desc, b.id",
                                BusinessEntity.class)
                        .setFirstResult(page * size)
                        .setMaxResults(size)
                        .getResultList()
                        .stream()
                        .map(BusinessEntity::toDomain)
                        .toList();
        long total =
                entityManager
                        .createQuery("select count(b) from BusinessEntity b", Long.class)
                        .getSingleResult();
        return new Page<>(items, total, page, size);
    }

    public Business save(Business business) {
        var entity = entityManager.merge(BusinessEntity.fromDomain(business));
        entityManager.flush();
        return entity.toDomain();
    }
}
