package com.stockflow.sales.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.categories.domain.CategoryRepository;
import com.stockflow.inventory.application.InventoryService;
import com.stockflow.inventory.domain.MovementType;
import com.stockflow.payments.application.PaymentService;
import com.stockflow.products.domain.ProductRepository;
import com.stockflow.sales.domain.*;
import com.stockflow.settings.domain.SettingsRepository;
import com.stockflow.shared.application.AuditService;
import com.stockflow.shared.domain.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SaleService {
    private final SaleRepository sales;
    private final SaleItemRepository items;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final SettingsRepository settings;
    private final PaymentService payments;
    private final InventoryService inventory;
    private final CurrentIdentity current;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final IdempotencyLock idempotency;

    public SaleService(
            SaleRepository sales,
            SaleItemRepository items,
            ProductRepository products,
            CategoryRepository categories,
            SettingsRepository settings,
            PaymentService payments,
            InventoryService inventory,
            CurrentIdentity current,
            ApplicationEventPublisher events,
            AuditService audit,
            IdempotencyLock idempotency) {
        this.sales = sales;
        this.items = items;
        this.products = products;
        this.categories = categories;
        this.settings = settings;
        this.payments = payments;
        this.inventory = inventory;
        this.current = current;
        this.events = events;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    public record SaleDetails(Sale sale, List<SaleItem> items) {}

    @Transactional(readOnly = true)
    public Page<Sale> history(int page, int size) {
        return sales.history(current.businessId(), page, size);
    }

    @Transactional(readOnly = true)
    public SaleDetails details(UUID id) {
        var sale = sales.find(current.businessId(), id).orElseThrow(BusinessException::notFound);
        return new SaleDetails(sale, items.forSale(current.businessId(), id));
    }

    public SaleDetails confirm(ConfirmSaleCommand command) {
        if (command.expectedTotal() == null
                || command.expectedTotal().signum() < 0
                || command.expectedTotal().scale() > 2
                || command.expectedTotal().precision() - command.expectedTotal().scale() > 16)
            throw new BusinessException("INVALID_TOTAL", 400);
        var businessId = current.businessId();
        idempotency.acquire(businessId, command.requestId());
        String fingerprint = fingerprint(command);
        var existing = sales.byRequest(businessId, command.requestId());
        if (existing.isPresent()) {
            if (!existing.get().requestFingerprint().equals(fingerprint))
                throw new BusinessException("IDEMPOTENCY_CONFLICT", 409);
            return details(existing.get().id());
        }
        var config = settings.get(businessId);
        if (!config.operatingMode().equals("POS")) throw new BusinessException("POS_DISABLED", 409);
        if (command.items().isEmpty()
                || command.items().size() > 100
                || command.items().stream()
                                .map(ConfirmSaleCommand.Line::productId)
                                .distinct()
                                .count()
                        != command.items().size()) {
            throw new BusinessException("INVALID_CART", 400);
        }
        var saleId = UUID.randomUUID();
        var taxRate = config.taxEnabled() ? config.taxRate() : BigDecimal.ZERO;
        var lines = priceAndLock(command.items(), businessId, saleId, taxRate);
        var totals =
                SaleCalculator.total(
                        lines.stream()
                                .map(
                                        line ->
                                                new SaleCalculator.LineAmount(
                                                        line.subtotal(), line.taxAmount()))
                                .toList());
        if (totals.total().compareTo(command.expectedTotal()) != 0)
            throw new BusinessException("PRICE_CHANGED", 409);
        var payment = payments.record(totals.total(), command.payment());
        var sale =
                sales.save(
                        new Sale(
                                saleId,
                                businessId,
                                current.userId(),
                                totals.subtotal(),
                                totals.taxAmount(),
                                totals.total(),
                                payment.method(),
                                payment.mode(),
                                payment.reference(),
                                "CONFIRMED",
                                command.requestId(),
                                fingerprint,
                                Instant.now()));
        lines.forEach(items::save);
        events.publishEvent(new SaleConfirmedEvent(businessId, saleId, current.userId(), lines));
        audit.record(businessId, current.userId(), "SALE_CONFIRMED", "sale", saleId);
        return new SaleDetails(sale, lines);
    }

    private List<SaleItem> priceAndLock(
            List<ConfirmSaleCommand.Line> requested,
            UUID businessId,
            UUID saleId,
            BigDecimal taxRate) {
        var result = new ArrayList<SaleItem>();
        for (var line :
                requested.stream()
                        .sorted(Comparator.comparing(value -> value.productId().toString()))
                        .toList()) {
            if (line.quantity() < 1 || line.quantity() > 1000000)
                throw new BusinessException("INVALID_CART", 400);
            var product =
                    products.lock(businessId, line.productId())
                            .orElseThrow(BusinessException::notFound);
            if (!product.active()) throw new BusinessException("PRODUCT_INACTIVE", 409);
            if (product.stock() < line.quantity())
                throw new BusinessException("INSUFFICIENT_STOCK", 409);
            var category =
                    categories
                            .find(businessId, product.categoryId())
                            .orElseThrow(BusinessException::notFound);
            var amount = SaleCalculator.line(product.salePrice(), line.quantity(), taxRate);
            result.add(
                    new SaleItem(
                            UUID.randomUUID(),
                            businessId,
                            saleId,
                            product.id(),
                            product.name(),
                            category.id(),
                            category.name(),
                            line.quantity(),
                            product.salePrice(),
                            amount.taxAmount(),
                            amount.subtotal()));
        }
        return List.copyOf(result);
    }

    public SaleDetails cancel(UUID id) {
        current.requireManager();
        var sale = sales.lock(current.businessId(), id).orElseThrow(BusinessException::notFound);
        if (sale.status().equals("CANCELLED")) return details(id);
        if (!sale.status().equals("CONFIRMED"))
            throw new BusinessException("INVALID_SALE_STATUS", 409);
        var lines = items.forSale(current.businessId(), id);
        lines.stream()
                .sorted(Comparator.comparing(item -> item.productId().toString()))
                .forEach(
                        item ->
                                inventory.apply(
                                        current.businessId(),
                                        current.userId(),
                                        item.productId(),
                                        MovementType.RETURN,
                                        item.quantity(),
                                        id));
        sales.save(
                new Sale(
                        sale.id(),
                        sale.businessId(),
                        sale.userId(),
                        sale.subtotal(),
                        sale.taxAmount(),
                        sale.total(),
                        sale.paymentMethod(),
                        sale.paymentMode(),
                        sale.paymentReference(),
                        "CANCELLED",
                        sale.requestId(),
                        sale.requestFingerprint(),
                        sale.createdAt()));
        audit.record(current.businessId(), current.userId(), "SALE_CANCELLED", "sale", id);
        return details(id);
    }

    private String fingerprint(ConfirmSaleCommand command) {
        var canonical =
                command.items().stream()
                                .sorted(Comparator.comparing(item -> item.productId().toString()))
                                .map(item -> item.productId() + ":" + item.quantity())
                                .reduce("", (left, right) -> left + "|" + right)
                        + "|"
                        + command.payment()
                        + "|"
                        + command.expectedTotal().stripTrailingZeros().toPlainString();
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
