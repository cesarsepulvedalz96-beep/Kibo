package com.stockflow.inventory.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.inventory.domain.*;
import com.stockflow.products.domain.*;
import com.stockflow.shared.application.AuditService;
import com.stockflow.shared.domain.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class InventoryService {
    private final ProductRepository products;
    private final InventoryMovementRepository movements;
    private final CurrentIdentity current;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public InventoryService(
            ProductRepository products,
            InventoryMovementRepository movements,
            CurrentIdentity current,
            AuditService audit,
            ApplicationEventPublisher events) {
        this.products = products;
        this.movements = movements;
        this.current = current;
        this.audit = audit;
        this.events = events;
    }

    @Transactional
    public InventoryMovement manual(UUID productId, MovementType type, int quantity) {
        current.requireManager();
        var product =
                products.lock(current.businessId(), productId)
                        .orElseThrow(BusinessException::notFound);
        int delta =
                switch (type) {
                    case PURCHASE_ENTRY, MANUAL_ENTRY -> quantity;
                    case MANUAL_EXIT -> -quantity;
                    case ADJUSTMENT -> quantity - product.stock();
                    default -> throw new BusinessException("INVALID_MOVEMENT", 400);
                };
        if (quantity == 0 && type != MovementType.ADJUSTMENT)
            throw new BusinessException("INVALID_MOVEMENT", 400);
        return apply(current.businessId(), current.userId(), productId, type, delta, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryMovement apply(
            UUID businessId,
            UUID userId,
            UUID productId,
            MovementType type,
            int delta,
            UUID referenceId) {
        var product = products.lock(businessId, productId).orElseThrow(BusinessException::notFound);
        if (!product.active() && type != MovementType.RETURN)
            throw new BusinessException("PRODUCT_INACTIVE", 409);
        int nextStock;
        try {
            nextStock = Math.addExact(product.stock(), delta);
        } catch (ArithmeticException exception) {
            throw new BusinessException("STOCK_LIMIT", 409);
        }
        var updated = products.save(product.withStock(nextStock));
        var movement =
                movements.save(
                        new InventoryMovement(
                                UUID.randomUUID(),
                                businessId,
                                productId,
                                type.name(),
                                delta,
                                product.stock(),
                                updated.stock(),
                                referenceId,
                                userId,
                                Instant.now()));
        audit.record(businessId, userId, "STOCK_" + type, "inventoryMovement", movement.id());
        events.publishEvent(
                new StockChangedEvent(product, updated, movement.id(), movement.type()));
        return movement;
    }
}
