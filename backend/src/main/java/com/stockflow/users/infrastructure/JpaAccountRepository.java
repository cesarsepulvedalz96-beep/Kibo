package com.stockflow.users.infrastructure;

import com.stockflow.shared.domain.Page;
import com.stockflow.users.domain.*;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAccountRepository implements AccountRepository {
    private final EntityManager entityManager;

    public JpaAccountRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<Account> find(UUID id) {
        return Optional.ofNullable(entityManager.find(AccountEntity.class, id))
                .map(AccountEntity::toDomain);
    }

    public Optional<Account> findByEmail(String email) {
        return entityManager
                .createQuery(
                        "select u from AccountEntity u where u.email = :email", AccountEntity.class)
                .setParameter("email", email)
                .getResultList()
                .stream()
                .findFirst()
                .map(AccountEntity::toDomain);
    }

    public Page<Account> list(UUID businessId, Boolean active, String search, int page, int size) {
        String filter =
                " where u.role = 'BUSINESS_USER'"
                        + " and (:businessId is null or u.businessId = :businessId)"
                        + " and (:active is null or u.active = :active)"
                        + " and (:search is null or lower(u.email) like :search or lower(u.displayName) like :search)";
        String normalizedSearch =
                search == null || search.isBlank()
                        ? null
                        : "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        var items =
                entityManager
                        .createQuery(
                                "select u from AccountEntity u"
                                        + filter
                                        + " order by u.createdAt desc, u.id",
                                AccountEntity.class)
                        .setParameter("businessId", businessId)
                        .setParameter("active", active)
                        .setParameter("search", normalizedSearch)
                        .setFirstResult(page * size)
                        .setMaxResults(size)
                        .getResultList()
                        .stream()
                        .map(AccountEntity::toDomain)
                        .toList();
        long total =
                entityManager
                        .createQuery("select count(u) from AccountEntity u" + filter, Long.class)
                        .setParameter("businessId", businessId)
                        .setParameter("active", active)
                        .setParameter("search", normalizedSearch)
                        .getSingleResult();
        return new Page<>(items, total, page, size);
    }

    public Account save(Account account) {
        var entity = entityManager.find(AccountEntity.class, account.id());
        if (entity == null) {
            entity = new AccountEntity();
            entity.update(account);
            entityManager.persist(entity);
        } else entity.update(account);
        entityManager.flush();
        return entity.toDomain();
    }
}
