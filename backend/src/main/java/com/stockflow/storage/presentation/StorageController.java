package com.stockflow.storage.presentation;

import com.stockflow.products.domain.Product;
import com.stockflow.storage.application.ProductImageService;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/storage/products")
public class StorageController {
    private final ProductImageService service;

    public StorageController(ProductImageService service) {
        this.service = service;
    }

    public record ImageUrl(URI url) {}

    @PostMapping(value = "/{id}", consumes = "multipart/form-data")
    Product upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file)
            throws IOException {
        return service.upload(id, file.getBytes());
    }

    @GetMapping("/{id}")
    ImageUrl read(@PathVariable UUID id) {
        return new ImageUrl(service.read(id));
    }
}
