package com.stockflow.billing.presentation;

import com.stockflow.billing.application.SubscriptionPlanService;
import com.stockflow.billing.domain.SubscriptionPlan;
import com.stockflow.shared.domain.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/admin/plans")
public class SubscriptionPlanController {
    private final SubscriptionPlanService service;

    public SubscriptionPlanController(SubscriptionPlanService service) {
        this.service = service;
    }

    public record PlanRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
                    BigDecimal monthlyPrice,
            @Min(1) @Max(3660) int billingPeriodDays,
            @Min(0) @Max(365) int gracePeriodDays,
            boolean active) {}

    @GetMapping
    Page<SubscriptionPlan> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(active, search, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SubscriptionPlan create(@Valid @RequestBody PlanRequest input) {
        return save(null, input);
    }

    @PutMapping("/{id}")
    SubscriptionPlan update(@PathVariable UUID id, @Valid @RequestBody PlanRequest input) {
        return save(id, input);
    }

    private SubscriptionPlan save(UUID id, PlanRequest input) {
        return service.save(
                id,
                input.name(),
                input.description(),
                input.monthlyPrice(),
                input.billingPeriodDays(),
                input.gracePeriodDays(),
                input.active());
    }
}
