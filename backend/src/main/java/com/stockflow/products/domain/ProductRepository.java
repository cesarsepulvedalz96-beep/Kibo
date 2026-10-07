package com.stockflow.products.domain;

import com.stockflow.shared.domain.TenantRepository;

public interface ProductRepository extends TenantRepository<Product> {
    com.stockflow.shared.domain.Page<Product> search(
            java.util.UUID businessId,
            String search,
            java.util.UUID categoryId,
            String status,
            int page,
            int size);

    java.util.Optional<Product> byBarcode(java.util.UUID businessId, String barcode);
}
