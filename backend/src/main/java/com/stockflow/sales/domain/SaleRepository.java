package com.stockflow.sales.domain;

import com.stockflow.shared.domain.TenantRepository;

public interface SaleRepository extends TenantRepository<Sale> {
    java.util.Optional<Sale> byRequest(java.util.UUID businessId, java.util.UUID requestId);

    com.stockflow.shared.domain.Page<Sale> history(java.util.UUID businessId, int page, int size);
}
