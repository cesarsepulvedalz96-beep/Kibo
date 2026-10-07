package com.stockflow.categories.domain;

import com.stockflow.shared.domain.TenantRepository;

public interface CategoryRepository extends TenantRepository<Category> {
    com.stockflow.shared.domain.Page<Category> search(
            java.util.UUID businessId, String search, String status, int page, int size);
}
