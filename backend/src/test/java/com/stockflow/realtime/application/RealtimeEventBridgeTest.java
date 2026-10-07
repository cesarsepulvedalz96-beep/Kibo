package com.stockflow.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.stockflow.billing.domain.SubscriptionChangedEvent;
import com.stockflow.inventory.domain.StockChangedEvent;
import com.stockflow.products.domain.Product;
import com.stockflow.sales.domain.SaleConfirmedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class RealtimeEventBridgeTest {
    @Test
    void mapsCommittedDomainEventsToMinimalInvalidationSignals() {
        var broker = mock(RealtimeBroker.class);
        var bridge = new RealtimeEventBridge(broker);
        var businessId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var movementId = UUID.randomUUID();
        var saleId = UUID.randomUUID();
        var subscriptionId = UUID.randomUUID();
        var before = product(businessId, productId, 1);
        var after = product(businessId, productId, 0);

        bridge.stockChanged(new StockChangedEvent(before, after, movementId, "SALE"));
        bridge.saleConfirmed(
                new SaleConfirmedEvent(businessId, saleId, UUID.randomUUID(), List.of()));
        bridge.subscriptionChanged(
                new SubscriptionChangedEvent(businessId, subscriptionId, "PAST_DUE"));

        var messages = ArgumentCaptor.forClass(RealtimeMessage.class);
        verify(broker, org.mockito.Mockito.times(4)).publish(eq(businessId), messages.capture());
        assertThat(messages.getAllValues())
                .extracting(RealtimeMessage::type)
                .contains(
                        "PRODUCT_STOCK_CHANGED",
                        "INVENTORY_CHANGED",
                        "SALE_CONFIRMED",
                        "SUBSCRIPTION_CHANGED");
        assertThat(messages.getAllValues())
                .extracting(RealtimeMessage::resourceId)
                .contains(productId, movementId, saleId, subscriptionId);
    }

    @Test
    void everyBridgeMethodRequiresACommittedTransaction() throws Exception {
        for (var methodName : List.of("stockChanged", "saleConfirmed", "subscriptionChanged")) {
            var method =
                    java.util.Arrays.stream(RealtimeEventBridge.class.getDeclaredMethods())
                            .filter(candidate -> candidate.getName().equals(methodName))
                            .findFirst()
                            .orElseThrow();
            var listener = method.getAnnotation(TransactionalEventListener.class);
            assertThat(listener).isNotNull();
            assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
            assertThat(listener.fallbackExecution()).isFalse();
        }
    }

    private Product product(UUID businessId, UUID productId, int stock) {
        var now = Instant.now();
        return new Product(
                productId,
                businessId,
                UUID.randomUUID(),
                "Product",
                null,
                null,
                BigDecimal.ONE,
                null,
                stock,
                0,
                null,
                true,
                now,
                now);
    }
}
