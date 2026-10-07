package com.stockflow.billing.domain;

import com.stockflow.shared.domain.Page;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository {
    Optional<SubscriptionPlan> find(UUID id);

    Optional<SubscriptionPlan> findDefaultActive();

    Page<SubscriptionPlan> list(Boolean active, String search, int page, int size);

    SubscriptionPlan save(SubscriptionPlan plan);
}
