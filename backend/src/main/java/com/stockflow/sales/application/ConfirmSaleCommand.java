package com.stockflow.sales.application;

import com.stockflow.payments.domain.PaymentStrategy.PaymentRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ConfirmSaleCommand(
        UUID requestId, List<Line> items, PaymentRequest payment, BigDecimal expectedTotal) {
    public ConfirmSaleCommand {
        items = List.copyOf(items);
    }

    public record Line(UUID productId, int quantity) {}
}
