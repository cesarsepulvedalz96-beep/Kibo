package com.stockflow.sales.domain;

import com.stockflow.shared.domain.TenantOwned;
import java.math.BigDecimal;
import java.util.UUID;

public record SaleItem(
        UUID id,
        UUID businessId,
        UUID saleId,
        UUID productId,
        String productNameSnapshot,
        UUID categoryIdSnapshot,
        String categoryNameSnapshot,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal taxAmount,
        BigDecimal subtotal)
        implements TenantOwned {}
