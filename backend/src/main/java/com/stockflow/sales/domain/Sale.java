package com.stockflow.sales.domain;

import com.stockflow.shared.domain.TenantOwned;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Sale(
        UUID id,
        UUID businessId,
        UUID userId,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total,
        String paymentMethod,
        String paymentMode,
        String paymentReference,
        String status,
        UUID requestId,
        String requestFingerprint,
        Instant createdAt)
        implements TenantOwned {}
