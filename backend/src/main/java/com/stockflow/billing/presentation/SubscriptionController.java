package com.stockflow.billing.presentation;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.billing.application.*;
import com.stockflow.billing.domain.SubscriptionStatus;
import com.stockflow.shared.domain.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class SubscriptionController {
    private final SubscriptionAdminService service;
    private final SubscriptionLifecycleService lifecycle;
    private final CurrentIdentity current;

    public SubscriptionController(
            SubscriptionAdminService service,
            SubscriptionLifecycleService lifecycle,
            CurrentIdentity current) {
        this.service = service;
        this.lifecycle = lifecycle;
        this.current = current;
    }

    public record AssignRequest(
            @NotNull UUID businessId, @NotNull UUID planId, @Min(1) @Max(3650) Integer trialDays) {}

    public record TrialRequest(@Min(1) @Max(3650) int trialDays) {}

    public record ChangePlanRequest(@NotNull UUID planId) {}

    @GetMapping("/api/v1/admin/subscriptions")
    Page<SubscriptionView> list(
            @RequestParam(required = false) UUID businessId,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(businessId, status, page, size);
    }

    @PostMapping("/api/v1/admin/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    SubscriptionView assign(@Valid @RequestBody AssignRequest input) {
        return service.assign(input.businessId(), input.planId(), input.trialDays());
    }

    @PostMapping("/api/v1/admin/subscriptions/{id}/start-trial")
    SubscriptionView startTrial(@PathVariable UUID id, @Valid @RequestBody TrialRequest input) {
        return service.startTrial(id, input.trialDays());
    }

    @PostMapping("/api/v1/admin/subscriptions/{id}/extend-trial")
    SubscriptionView extendTrial(@PathVariable UUID id, @Valid @RequestBody TrialRequest input) {
        return service.extendTrial(id, input.trialDays());
    }

    @PostMapping("/api/v1/admin/subscriptions/{id}/change-plan")
    SubscriptionView changePlan(
            @PathVariable UUID id, @Valid @RequestBody ChangePlanRequest input) {
        return service.changePlan(id, input.planId());
    }

    @PostMapping("/api/v1/admin/subscriptions/{id}/activate")
    SubscriptionView activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/api/v1/admin/subscriptions/{id}/suspend")
    SubscriptionView suspend(@PathVariable UUID id) {
        return service.suspend(id);
    }

    @PostMapping("/api/v1/admin/subscriptions/{id}/reactivate")
    SubscriptionView reactivate(@PathVariable UUID id) {
        return service.reactivate(id);
    }

    @PostMapping("/api/v1/admin/subscriptions/{id}/cancel")
    SubscriptionView cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @GetMapping("/api/v1/subscription")
    SubscriptionView currentSubscription() {
        current.requireManager();
        return service.view(lifecycle.refresh(current.businessId()));
    }
}
