package com.stockflow.products.infrastructure;

import com.stockflow.products.domain.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "ProductEntity")
@Table(name = "products")
public class ProductEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "sale_price")
    private BigDecimal salePrice;

    @Column(name = "cost_price")
    private BigDecimal costPrice;

    @Column(name = "stock")
    private int stock;

    @Column(name = "minimum_stock")
    private int minimumStock;

    @Column(name = "image_key")
    private String imageKey;

    @Column(name = "active")
    private boolean active;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ProductEntity() {}

    static ProductEntity fromDomain(Product model) {
        var entity = new ProductEntity();
        entity.id = model.id();
        entity.businessId = model.businessId();
        entity.categoryId = model.categoryId();
        entity.name = model.name();
        entity.description = model.description();
        entity.barcode = model.barcode();
        entity.salePrice = model.salePrice();
        entity.costPrice = model.costPrice();
        entity.stock = model.stock();
        entity.minimumStock = model.minimumStock();
        entity.imageKey = model.imageKey();
        entity.active = model.active();
        entity.createdAt = model.createdAt();
        entity.updatedAt = model.updatedAt();
        return entity;
    }

    Product toDomain() {
        return new Product(
                id,
                businessId,
                categoryId,
                name,
                description,
                barcode,
                salePrice,
                costPrice,
                stock,
                minimumStock,
                imageKey,
                active,
                createdAt,
                updatedAt);
    }
}
