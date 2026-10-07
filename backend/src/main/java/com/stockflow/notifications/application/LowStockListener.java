package com.stockflow.notifications.application;

import com.stockflow.inventory.domain.StockChangedEvent;
import com.stockflow.notifications.domain.*;
import com.stockflow.settings.domain.SettingsRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class LowStockListener {
    private final NotificationRepository notifications;
    private final SettingsRepository settings;

    public LowStockListener(NotificationRepository notifications, SettingsRepository settings) {
        this.notifications = notifications;
        this.settings = settings;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void changed(StockChangedEvent event) {
        var product = event.after();
        if (product.stock() != event.before().stock()
                && product.isLowStock()
                && settings.get(product.businessId()).stockAlerts()) {
            notifications.save(
                    new Notification(
                            UUID.randomUUID(),
                            product.businessId(),
                            product.id(),
                            product.name(),
                            product.stock(),
                            false,
                            Instant.now()));
        }
    }
}
