package com.stockflow.shared.domain;

import java.util.Optional;
import java.util.UUID;

/** Every read requires the business from the authenticated identity. */
public interface TenantRepository<T extends TenantOwned> {
    Optional<T> find(UUID businessId, UUID id);

    Optional<T> lock(UUID businessId, UUID id);

    Page<T> list(UUID businessId, int page, int size);

    T save(T model);
}
