package com.stockflow.products.domain;

import com.stockflow.shared.domain.TenantOwned;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Product(
        UUID id,
        UUID businessId,
        UUID categoryId,
        String name,
        String description,
        String barcode,
        BigDecimal salePrice,
        BigDecimal costPrice,
        int stock,
        int minimumStock,
        String imageKey,
        boolean active,
        Instant createdAt,
        Instant updatedAt)
        implements TenantOwned {
    public Product withStock(int newStock) {
        if (newStock < 0)
            throw new com.stockflow.shared.domain.BusinessException("INSUFFICIENT_STOCK", 409);
        return new Product(
                id,
                businessId,
                categoryId,
                name,
                description,
                barcode,
                salePrice,
                costPrice,
                newStock,
                minimumStock,
                imageKey,
                active,
                createdAt,
                Instant.now());
    }

    public boolean isLowStock() {
        return active && stock <= minimumStock;
    }
}
