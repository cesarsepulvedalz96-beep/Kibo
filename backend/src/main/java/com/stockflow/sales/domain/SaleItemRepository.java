package com.stockflow.sales.domain;

import com.stockflow.shared.domain.TenantRepository;

public interface SaleItemRepository extends TenantRepository<SaleItem> {
    java.util.List<SaleItem> forSale(java.util.UUID businessId, java.util.UUID saleId);
}
