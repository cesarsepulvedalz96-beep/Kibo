package com.stockflow.shared.infrastructure;

import com.stockflow.shared.domain.*;
import jakarta.persistence.*;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Shared persistence mechanism; all JPQL reads contain an explicit tenant predicate. */
public class JpaTenantRepository<T extends TenantOwned, E> implements TenantRepository<T> {
    protected final EntityManager entityManager;
    protected final Class<E> entityClass;
    protected final Function<E, T> toDomain;
    private final Function<T, E> toEntity;

    public JpaTenantRepository(
            EntityManager entityManager,
            Class<E> entityClass,
            Function<T, E> toEntity,
            Function<E, T> toDomain) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.toEntity = toEntity;
        this.toDomain = toDomain;
    }

    @Override
    public Optional<T> find(UUID businessId, UUID id) {
        return find(businessId, id, false);
    }

    @Override
    public Optional<T> lock(UUID businessId, UUID id) {
        return find(businessId, id, true);
    }

    private Optional<T> find(UUID businessId, UUID id, boolean lock) {
        var query =
                entityManager
                        .createQuery(
                                "select e from "
                                        + entityClass.getSimpleName()
                                        + " e where e.businessId = :business and e.id = :id",
                                entityClass)
                        .setParameter("business", businessId)
                        .setParameter("id", id);
        if (lock) query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultList().stream().findFirst().map(toDomain);
    }

    @Override
    public Page<T> list(UUID businessId, int page, int size) {
        var predicate =
                " from " + entityClass.getSimpleName() + " e where e.businessId = :business";
        var items =
                entityManager
                        .createQuery("select e" + predicate + " order by e.id", entityClass)
                        .setParameter("business", businessId)
                        .setFirstResult(page * size)
                        .setMaxResults(size)
                        .getResultList()
                        .stream()
                        .map(toDomain)
                        .toList();
        var count =
                entityManager
                        .createQuery("select count(e)" + predicate, Long.class)
                        .setParameter("business", businessId)
                        .getSingleResult();
        return new Page<>(items, count, page, size);
    }

    @Override
    public T save(T model) {
        var entity = entityManager.merge(toEntity.apply(model));
        entityManager.flush();
        return toDomain.apply(entity);
    }
}
