package com.stockflow.notifications.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.notifications.domain.*;
import com.stockflow.shared.domain.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {
    private final NotificationRepository notifications;
    private final CurrentIdentity current;

    public NotificationService(NotificationRepository notifications, CurrentIdentity current) {
        this.notifications = notifications;
        this.current = current;
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(int page, int size) {
        return notifications.list(current.businessId(), page, size);
    }

    public void read(UUID id) {
        if (!notifications.markRead(current.businessId(), id)) {
            throw BusinessException.notFound();
        }
    }
}
