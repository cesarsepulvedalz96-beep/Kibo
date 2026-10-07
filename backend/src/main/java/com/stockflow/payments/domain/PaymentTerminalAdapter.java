package com.stockflow.payments.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Future provider port. Never implement this with an unconditional approval. */
public interface PaymentTerminalAdapter {
    Authorization authorize(UUID operationId, BigDecimal total, String currency);

    record Authorization(String externalId, Status status) {}

    enum Status {
        APPROVED,
        REJECTED,
        PENDING
    }
}
