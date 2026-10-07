package com.stockflow.products.presentation;

import com.stockflow.products.application.ProductService;
import com.stockflow.products.domain.Product;
import com.stockflow.shared.domain.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    public record ProductRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 2000) String description,
            @Size(max = 80) String barcode,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal salePrice,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal costPrice,
            @Min(0) @Max(1000000) int minimumStock,
            boolean active) {}

    @GetMapping
    Page<Product> list(
            @RequestParam(defaultValue = "") @Size(max = 160) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "all") @Pattern(regexp = "all|active|inactive|low")
                    String status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(search, categoryId, status, page, size);
    }

    @GetMapping("/{id}")
    Product get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/barcode/{barcode}")
    Product barcode(@PathVariable @Size(min = 1, max = 80) String barcode) {
        return service.barcode(barcode);
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Product create(@Valid @RequestBody ProductRequest input) {
        return save(null, input);
    }

    @PutMapping("/{id}")
    Product update(@PathVariable UUID id, @Valid @RequestBody ProductRequest input) {
        return save(id, input);
    }

    private Product save(UUID id, ProductRequest input) {
        return service.save(
                id,
                input.categoryId(),
                input.name(),
                input.description(),
                input.barcode(),
                input.salePrice(),
                input.costPrice(),
                input.minimumStock(),
                input.active());
    }
}
