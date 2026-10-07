package com.stockflow.sales.domain;

import java.util.List;
import java.util.UUID;

public record SaleConfirmedEvent(UUID businessId, UUID saleId, UUID userId, List<SaleItem> items) {
    public SaleConfirmedEvent {
        items = List.copyOf(items);
    }
}
