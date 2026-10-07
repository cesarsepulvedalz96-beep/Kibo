package com.stockflow.sales.infrastructure;

import com.stockflow.sales.domain.SaleItem;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity(name = "SaleItemEntity")
@Table(name = "sale_items")
public class SaleItemEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "sale_id")
    private UUID saleId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "product_name_snapshot")
    private String productNameSnapshot;

    @Column(name = "category_id_snapshot")
    private UUID categoryIdSnapshot;

    @Column(name = "category_name_snapshot")
    private String categoryNameSnapshot;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    @Column(name = "subtotal")
    private BigDecimal subtotal;

    protected SaleItemEntity() {}

    static SaleItemEntity fromDomain(SaleItem model) {
        var entity = new SaleItemEntity();
        entity.id = model.id();
        entity.businessId = model.businessId();
        entity.saleId = model.saleId();
        entity.productId = model.productId();
        entity.productNameSnapshot = model.productNameSnapshot();
        entity.categoryIdSnapshot = model.categoryIdSnapshot();
        entity.categoryNameSnapshot = model.categoryNameSnapshot();
        entity.quantity = model.quantity();
        entity.unitPrice = model.unitPrice();
        entity.taxAmount = model.taxAmount();
        entity.subtotal = model.subtotal();
        return entity;
    }

    SaleItem toDomain() {
        return new SaleItem(
                id,
                businessId,
                saleId,
                productId,
                productNameSnapshot,
                categoryIdSnapshot,
                categoryNameSnapshot,
                quantity,
                unitPrice,
                taxAmount,
                subtotal);
    }
}
