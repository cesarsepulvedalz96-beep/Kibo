package com.stockflow.shared.domain;

import java.util.UUID;

/** Serializes retries for one business and request within the caller's transaction. */
public interface IdempotencyLock {
    void acquire(UUID businessId, UUID requestId);
}
