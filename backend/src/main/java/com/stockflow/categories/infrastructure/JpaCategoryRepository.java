package com.stockflow.categories.infrastructure;

import com.stockflow.categories.domain.*;
import com.stockflow.shared.infrastructure.JpaTenantRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCategoryRepository extends JpaTenantRepository<Category, CategoryEntity>
        implements CategoryRepository {
    public JpaCategoryRepository(EntityManager entityManager) {
        super(
                entityManager,
                CategoryEntity.class,
                CategoryEntity::fromDomain,
                CategoryEntity::toDomain);
    }

    @Override
    public com.stockflow.shared.domain.Page<Category> search(
            java.util.UUID businessId, String search, String status, int page, int size) {
        String predicate =
                " from CategoryEntity c where c.businessId = :business and lower(c.name) like :search"
                        + (status.equals("active")
                                ? " and c.active = true"
                                : status.equals("inactive") ? " and c.active = false" : "");
        var query =
                entityManager.createQuery(
                        "select c" + predicate + " order by lower(c.name), c.id",
                        CategoryEntity.class);
        var count = entityManager.createQuery("select count(c)" + predicate, Long.class);
        for (var q : java.util.List.of(query, count)) {
            q.setParameter("business", businessId)
                    .setParameter("search", "%" + search.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        return new com.stockflow.shared.domain.Page<>(
                query.setFirstResult(page * size).setMaxResults(size).getResultList().stream()
                        .map(toDomain)
                        .toList(),
                count.getSingleResult(),
                page,
                size);
    }
}
