package com.stockflow.realtime.application;

import com.stockflow.billing.domain.SubscriptionChangedEvent;
import com.stockflow.inventory.domain.StockChangedEvent;
import com.stockflow.sales.domain.SaleConfirmedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RealtimeEventBridge {
    private final RealtimeBroker broker;

    public RealtimeEventBridge(RealtimeBroker broker) {
        this.broker = broker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void stockChanged(StockChangedEvent event) {
        broker.publish(
                event.after().businessId(),
                RealtimeMessage.now("PRODUCT_STOCK_CHANGED", event.after().id()));
        broker.publish(
                event.after().businessId(),
                RealtimeMessage.now("INVENTORY_CHANGED", event.movementId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void saleConfirmed(SaleConfirmedEvent event) {
        broker.publish(event.businessId(), RealtimeMessage.now("SALE_CONFIRMED", event.saleId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void subscriptionChanged(SubscriptionChangedEvent event) {
        broker.publish(
                event.businessId(),
                RealtimeMessage.now("SUBSCRIPTION_CHANGED", event.subscriptionId()));
    }
}
