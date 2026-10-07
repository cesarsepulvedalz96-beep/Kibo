package com.stockflow.notifications.domain;

import com.stockflow.shared.domain.Page;
import com.stockflow.shared.domain.TenantRepository;
import java.util.UUID;

public interface NotificationRepository extends TenantRepository<Notification> {
    Page<Notification> list(UUID businessId, int page, int size);

    boolean markRead(UUID businessId, UUID id);
}
