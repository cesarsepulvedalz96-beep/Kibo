package com.stockflow.inventory.domain;

import com.stockflow.products.domain.Product;
import java.util.UUID;

public record StockChangedEvent(
        Product before, Product after, UUID movementId, String movementType) {}
