package com.stockflow.billing.presentation;

import com.stockflow.billing.application.SubscriptionPaymentService;
import com.stockflow.billing.application.SubscriptionPaymentView;
import com.stockflow.billing.domain.PaymentStatus;
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
public class SubscriptionPaymentController {
    private final SubscriptionPaymentService service;

    public SubscriptionPaymentController(SubscriptionPaymentService service) {
        this.service = service;
    }

    public record AdminPaymentRequest(
            @NotNull UUID businessId,
            @NotNull UUID requestId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 30) String paymentMethod,
            @Size(max = 160) String externalReference) {}

    public record BusinessPaymentRequest(
            @NotNull UUID requestId,
            @NotBlank @Size(max = 30) String paymentMethod,
            @Size(max = 160) String externalReference) {}

    public record ConfirmPaymentRequest(@Size(max = 160) String externalReference) {}

    @GetMapping("/api/v1/admin/subscription-payments")
    Page<SubscriptionPaymentView> listAdmin(
            @RequestParam(required = false) UUID businessId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listAdmin(businessId, status, page, size);
    }

    @PostMapping("/api/v1/admin/subscription-payments")
    @ResponseStatus(HttpStatus.CREATED)
    SubscriptionPaymentView registerAdmin(@Valid @RequestBody AdminPaymentRequest input) {
        return service.registerAdmin(
                input.businessId(),
                input.requestId(),
                input.amount(),
                input.paymentMethod(),
                input.externalReference());
    }

    @PostMapping("/api/v1/admin/subscription-payments/{id}/confirm")
    SubscriptionPaymentView confirm(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ConfirmPaymentRequest input) {
        return service.confirm(id, input == null ? null : input.externalReference());
    }

    @GetMapping("/api/v1/subscription/payments")
    Page<SubscriptionPaymentView> listCurrent(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listForCurrentBusiness(status, page, size);
    }

    @PostMapping("/api/v1/subscription/payments")
    @ResponseStatus(HttpStatus.CREATED)
    SubscriptionPaymentView request(@Valid @RequestBody BusinessPaymentRequest input) {
        return service.requestForCurrentBusiness(
                input.requestId(), input.paymentMethod(), input.externalReference());
    }
}
