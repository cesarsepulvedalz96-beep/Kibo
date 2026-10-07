package com.stockflow.storage.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.products.domain.*;
import com.stockflow.shared.domain.BusinessException;
import com.stockflow.storage.domain.StorageAdapter;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductImageService {
    private final StorageAdapter storage;
    private final ProductRepository products;
    private final ImageSanitizer images;
    private final CurrentIdentity current;

    public ProductImageService(
            StorageAdapter storage,
            ProductRepository products,
            ImageSanitizer images,
            CurrentIdentity current) {
        this.storage = storage;
        this.products = products;
        this.images = images;
        this.current = current;
    }

    @Transactional
    public Product upload(UUID productId, byte[] input) {
        current.requireManager();
        var product =
                products.lock(current.businessId(), productId)
                        .orElseThrow(BusinessException::notFound);
        var bytes = images.sanitize(input);
        String key =
                "businesses/"
                        + current.businessId()
                        + "/products/"
                        + productId
                        + "/"
                        + UUID.randomUUID()
                        + ".png";
        storage.put(key, bytes, "image/png");
        return products.save(
                new Product(
                        product.id(),
                        product.businessId(),
                        product.categoryId(),
                        product.name(),
                        product.description(),
                        product.barcode(),
                        product.salePrice(),
                        product.costPrice(),
                        product.stock(),
                        product.minimumStock(),
                        key,
                        product.active(),
                        product.createdAt(),
                        Instant.now()));
    }

    @Transactional(readOnly = true)
    public URI read(UUID productId) {
        var product =
                products.find(current.businessId(), productId)
                        .orElseThrow(BusinessException::notFound);
        if (product.imageKey() == null) throw BusinessException.notFound();
        return storage.signedRead(product.imageKey());
    }
}
