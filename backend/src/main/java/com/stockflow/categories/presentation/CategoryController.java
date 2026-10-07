package com.stockflow.categories.presentation;

import com.stockflow.categories.application.CategoryService;
import com.stockflow.categories.domain.Category;
import com.stockflow.shared.domain.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    public record CategoryRequest(@NotBlank @Size(max = 100) String name, boolean active) {}

    @GetMapping
    Page<Category> list(
            @RequestParam(defaultValue = "") @Size(max = 100) String search,
            @RequestParam(defaultValue = "all") @Pattern(regexp = "all|active|inactive")
                    String status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(search, status, page, size);
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Category create(@Valid @RequestBody CategoryRequest input) {
        return service.save(null, input.name(), input.active());
    }

    @PutMapping("/{id}")
    Category update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest input) {
        return service.save(id, input.name(), input.active());
    }
}
