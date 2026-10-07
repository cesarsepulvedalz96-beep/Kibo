package com.stockflow.billing.domain;

import com.stockflow.shared.domain.Page;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {
    Optional<Subscription> find(UUID id);

    Optional<Subscription> findByBusiness(UUID businessId);

    Optional<Subscription> lockByBusiness(UUID businessId);

    Page<Subscription> list(UUID businessId, SubscriptionStatus status, int page, int size);

    List<UUID> findBusinessesDue(Instant now, int limit);

    Subscription save(Subscription subscription);
}
