package com.stockflow.businesses.presentation;

import com.stockflow.businesses.application.BusinessService;
import com.stockflow.businesses.application.BusinessView;
import com.stockflow.shared.domain.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/admin/businesses")
public class BusinessController {
    private final BusinessService service;

    public BusinessController(BusinessService service) {
        this.service = service;
    }

    public record BusinessRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 40) String taxId,
            boolean active,
            UUID planId,
            @Min(1) @Max(3650) Integer trialDays) {}

    @GetMapping
    Page<BusinessView> list(
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(page, size);
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    BusinessView create(@Valid @RequestBody BusinessRequest input) {
        return service.save(
                null,
                input.name(),
                input.taxId(),
                input.active(),
                input.planId(),
                input.trialDays());
    }

    @PutMapping("/{id}")
    BusinessView update(@PathVariable UUID id, @Valid @RequestBody BusinessRequest input) {
        return service.save(
                id, input.name(), input.taxId(), input.active(), input.planId(), input.trialDays());
    }
}
