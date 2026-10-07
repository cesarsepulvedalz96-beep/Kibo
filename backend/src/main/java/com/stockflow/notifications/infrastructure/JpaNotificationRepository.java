package com.stockflow.notifications.infrastructure;

import com.stockflow.notifications.domain.*;
import com.stockflow.shared.domain.Page;
import com.stockflow.shared.infrastructure.JpaTenantRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaNotificationRepository extends JpaTenantRepository<Notification, NotificationEntity>
        implements NotificationRepository {
    public JpaNotificationRepository(EntityManager entityManager) {
        super(
                entityManager,
                NotificationEntity.class,
                NotificationEntity::fromDomain,
                NotificationEntity::toDomain);
    }

    @Override
    public Page<Notification> list(UUID businessId, int page, int size) {
        var items =
                entityManager
                        .createQuery(
                                "select n from NotificationEntity n where n.businessId = :businessId order by n.createdAt desc, n.id",
                                NotificationEntity.class)
                        .setParameter("businessId", businessId)
                        .setFirstResult(page * size)
                        .setMaxResults(size)
                        .getResultStream()
                        .map(NotificationEntity::toDomain)
                        .toList();
        long total =
                entityManager
                        .createQuery(
                                "select count(n) from NotificationEntity n where n.businessId = :businessId",
                                Long.class)
                        .setParameter("businessId", businessId)
                        .getSingleResult();
        return new Page<>(items, total, page, size);
    }

    @Override
    public boolean markRead(UUID businessId, UUID id) {
        return entityManager
                        .createQuery(
                                "update NotificationEntity n set n.read = true where n.businessId = :businessId and n.id = :id")
                        .setParameter("businessId", businessId)
                        .setParameter("id", id)
                        .executeUpdate()
                > 0;
    }
}
