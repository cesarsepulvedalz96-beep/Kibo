package com.stockflow.businesses.domain;

import com.stockflow.shared.domain.Page;
import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository {
    Optional<Business> find(UUID id);

    Page<Business> list(int page, int size);

    Business save(Business business);
}
