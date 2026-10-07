package com.stockflow.products.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.categories.domain.CategoryRepository;
import com.stockflow.products.domain.*;
import com.stockflow.shared.application.AuditService;
import com.stockflow.shared.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CurrentIdentity current;
    private final AuditService audit;

    public ProductService(
            ProductRepository products,
            CategoryRepository categories,
            CurrentIdentity current,
            AuditService audit) {
        this.products = products;
        this.categories = categories;
        this.current = current;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<Product> search(String search, UUID categoryId, String status, int page, int size) {
        return products.search(current.businessId(), search, categoryId, status, page, size);
    }

    @Transactional(readOnly = true)
    public Product get(UUID id) {
        return products.find(current.businessId(), id).orElseThrow(BusinessException::notFound);
    }

    @Transactional(readOnly = true)
    public Product barcode(String barcode) {
        return products.byBarcode(current.businessId(), barcode)
                .orElseThrow(BusinessException::notFound);
    }

    public Product save(
            UUID id,
            UUID categoryId,
            String name,
            String description,
            String barcode,
            BigDecimal salePrice,
            BigDecimal costPrice,
            int minimumStock,
            boolean active) {
        current.requireManager();
        var previous =
                id == null
                        ? null
                        : products.lock(current.businessId(), id)
                                .orElseThrow(BusinessException::notFound);
        var category =
                categories
                        .find(current.businessId(), categoryId)
                        .orElseThrow(BusinessException::notFound);
        if (!category.active() && (previous == null || !previous.categoryId().equals(categoryId))) {
            throw new BusinessException("CATEGORY_INACTIVE", 409);
        }
        var product =
                products.save(
                        new Product(
                                id == null ? UUID.randomUUID() : id,
                                current.businessId(),
                                categoryId,
                                name.trim(),
                                description,
                                barcode == null || barcode.isBlank() ? null : barcode.trim(),
                                Money.round(salePrice),
                                costPrice == null ? null : Money.round(costPrice),
                                previous == null ? 0 : previous.stock(),
                                minimumStock,
                                previous == null ? null : previous.imageKey(),
                                active,
                                previous == null ? Instant.now() : previous.createdAt(),
                                Instant.now()));
        audit.record(
                current.businessId(),
                current.userId(),
                previous == null ? "PRODUCT_CREATED" : "PRODUCT_UPDATED",
                "product",
                product.id());
        if (previous != null && previous.salePrice().compareTo(product.salePrice()) != 0) {
            audit.record(
                    current.businessId(),
                    current.userId(),
                    "PRICE_CHANGED",
                    "product",
                    product.id());
        }
        return product;
    }
}
