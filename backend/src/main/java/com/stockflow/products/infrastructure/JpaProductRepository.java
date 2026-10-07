package com.stockflow.products.infrastructure;

import com.stockflow.products.domain.*;
import com.stockflow.shared.infrastructure.JpaTenantRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class JpaProductRepository extends JpaTenantRepository<Product, ProductEntity>
        implements ProductRepository {
    public JpaProductRepository(EntityManager entityManager) {
        super(
                entityManager,
                ProductEntity.class,
                ProductEntity::fromDomain,
                ProductEntity::toDomain);
    }

    @Override
    public com.stockflow.shared.domain.Page<Product> search(
            java.util.UUID businessId,
            String search,
            java.util.UUID categoryId,
            String status,
            int page,
            int size) {
        String predicate =
                " from ProductEntity p where p.businessId = :business"
                        + " and (lower(p.name) like :search or lower(p.barcode) like :search)"
                        + (categoryId == null ? "" : " and p.categoryId = :category")
                        + switch (status) {
                            case "active" -> " and p.active = true";
                            case "inactive" -> " and p.active = false";
                            case "low" -> " and p.active = true and p.stock <= p.minimumStock";
                            default -> "";
                        };
        var query =
                entityManager.createQuery(
                        "select p" + predicate + " order by lower(p.name), p.id",
                        ProductEntity.class);
        var count = entityManager.createQuery("select count(p)" + predicate, Long.class);
        for (var q : java.util.List.of(query, count)) {
            q.setParameter("business", businessId);
            q.setParameter("search", "%" + search.toLowerCase(java.util.Locale.ROOT) + "%");
            if (categoryId != null) q.setParameter("category", categoryId);
        }
        var items =
                query.setFirstResult(page * size).setMaxResults(size).getResultList().stream()
                        .map(toDomain)
                        .toList();
        return new com.stockflow.shared.domain.Page<>(items, count.getSingleResult(), page, size);
    }

    @Override
    public java.util.Optional<Product> byBarcode(java.util.UUID businessId, String barcode) {
        return entityManager
                .createQuery(
                        "select p from ProductEntity p where p.businessId = :business and p.barcode = :barcode and p.active = true",
                        ProductEntity.class)
                .setParameter("business", businessId)
                .setParameter("barcode", barcode)
                .getResultList()
                .stream()
                .findFirst()
                .map(toDomain);
    }
}
