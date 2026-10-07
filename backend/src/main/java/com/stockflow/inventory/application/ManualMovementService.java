package com.stockflow.inventory.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.inventory.domain.*;
import com.stockflow.shared.domain.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualMovementService {
    private final CurrentIdentity current;
    private final InventoryService inventory;
    private final InventoryMovementRepository movements;
    private final ManualMovementRequestRepository requests;
    private final IdempotencyLock locks;

    public ManualMovementService(
            CurrentIdentity current,
            InventoryService inventory,
            InventoryMovementRepository movements,
            ManualMovementRequestRepository requests,
            IdempotencyLock locks) {
        this.current = current;
        this.inventory = inventory;
        this.movements = movements;
        this.requests = requests;
        this.locks = locks;
    }

    @Transactional
    public InventoryMovement confirm(
            UUID requestId, UUID productId, MovementType type, int quantity) {
        current.requireManager();
        var businessId = current.businessId();
        if (requestId == null
                || productId == null
                || type == null
                || quantity < 0
                || quantity > 1000000) throw new BusinessException("INVALID_MOVEMENT", 400);
        locks.acquire(businessId, requestId);
        String fingerprint = productId + ":" + type + ":" + quantity;
        var previous = requests.find(businessId, requestId);
        if (previous.isPresent()) {
            if (!previous.get().fingerprint().equals(fingerprint))
                throw new BusinessException("IDEMPOTENCY_CONFLICT", 409);
            return movements
                    .find(businessId, previous.get().movementId())
                    .orElseThrow(BusinessException::notFound);
        }
        var movement = inventory.manual(productId, type, quantity);
        requests.record(businessId, requestId, fingerprint, movement.id());
        return movement;
    }
}
