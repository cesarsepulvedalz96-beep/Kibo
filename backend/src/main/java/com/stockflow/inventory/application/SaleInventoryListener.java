package com.stockflow.inventory.application;

import com.stockflow.inventory.domain.MovementType;
import com.stockflow.sales.domain.SaleConfirmedEvent;
import java.util.Comparator;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class SaleInventoryListener {
    private final InventoryService inventory;

    public SaleInventoryListener(InventoryService inventory) {
        this.inventory = inventory;
    }

    /** Synchronous: a failure rolls back sale, payment record, stock, audit and notifications. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmed(SaleConfirmedEvent event) {
        event.items().stream()
                .sorted(Comparator.comparing(item -> item.productId().toString()))
                .forEach(
                        item ->
                                inventory.apply(
                                        event.businessId(),
                                        event.userId(),
                                        item.productId(),
                                        MovementType.SALE,
                                        -item.quantity(),
                                        event.saleId()));
    }
}
