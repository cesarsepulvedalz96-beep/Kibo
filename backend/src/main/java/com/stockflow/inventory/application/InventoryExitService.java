package com.stockflow.inventory.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.inventory.domain.InventoryOperationRepository;
import com.stockflow.inventory.domain.MovementType;
import com.stockflow.settings.domain.SettingsRepository;
import com.stockflow.shared.domain.BusinessException;
import com.stockflow.shared.domain.IdempotencyLock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryExitService {
    private final InventoryService inventory;
    private final CurrentIdentity current;
    private final SettingsRepository settings;
    private final IdempotencyLock idempotency;
    private final InventoryOperationRepository operations;

    public InventoryExitService(
            InventoryService inventory,
            CurrentIdentity current,
            SettingsRepository settings,
            IdempotencyLock idempotency,
            InventoryOperationRepository operations) {
        this.inventory = inventory;
        this.current = current;
        this.settings = settings;
        this.idempotency = idempotency;
        this.operations = operations;
    }

    public record ExitLine(UUID productId, int quantity) {}

    public record ExitResult(UUID id) {}

    @Transactional
    public ExitResult confirm(UUID requestId, List<ExitLine> items) {
        var businessId = current.businessId();
        idempotency.acquire(businessId, requestId);
        if (items.isEmpty()
                || items.size() > 100
                || items.stream().anyMatch(item -> item.quantity() < 1)
                || items.stream().map(ExitLine::productId).distinct().count() != items.size()) {
            throw new BusinessException("INVALID_CART", 400);
        }
        var sorted =
                items.stream()
                        .sorted(Comparator.comparing(item -> item.productId().toString()))
                        .toList();
        String fingerprint;
        try {
            fingerprint =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    sorted.toString()
                                                            .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        var previous = operations.byRequest(businessId, requestId);
        if (previous.isPresent()) {
            if (!previous.get().requestFingerprint().equals(fingerprint))
                throw new BusinessException("IDEMPOTENCY_CONFLICT", 409);
            return new ExitResult(previous.get().id());
        }
        if (!settings.get(businessId).operatingMode().equals("INVENTORY_ONLY"))
            throw new BusinessException("INVENTORY_ONLY_REQUIRED", 409);
        var operationId = UUID.randomUUID();
        operations.record(operationId, businessId, requestId, fingerprint, current.userId());
        sorted.forEach(
                item ->
                        inventory.apply(
                                businessId,
                                current.userId(),
                                item.productId(),
                                MovementType.MANUAL_EXIT,
                                -item.quantity(),
                                operationId));
        return new ExitResult(operationId);
    }
}
