package com.stockflow.billing.domain;

import com.stockflow.shared.domain.Page;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPaymentRepository {
    Optional<SubscriptionPayment> find(UUID id);

    Optional<SubscriptionPayment> lock(UUID id);

    Optional<SubscriptionPayment> findByRequest(UUID businessId, UUID requestId);

    Page<SubscriptionPayment> list(UUID businessId, PaymentStatus status, int page, int size);

    SubscriptionPayment save(SubscriptionPayment payment);
}
