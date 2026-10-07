package com.stockflow.sales.presentation;

import com.stockflow.payments.domain.PaymentStrategy.PaymentRequest;
import com.stockflow.sales.application.*;
import com.stockflow.sales.domain.Sale;
import com.stockflow.shared.domain.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/sales")
public class SaleController {
    private final SaleService service;

    public SaleController(SaleService service) {
        this.service = service;
    }

    public record LineRequest(@NotNull UUID productId, @Min(1) @Max(1000000) int quantity) {}

    public record ConfirmRequest(
            @NotNull UUID requestId,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal expectedTotal,
            @NotEmpty @Size(max = 100) List<@Valid LineRequest> items,
            @NotNull @Pattern(regexp = "CASH|CARD|TRANSFER|EXTERNAL") String paymentMethod,
            @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal cashReceived,
            @Size(max = 120) String paymentReference,
            boolean externallyConfirmed) {}

    @GetMapping
    Page<Sale> list(
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.history(page, size);
    }

    @GetMapping("/{id}")
    SaleService.SaleDetails get(@PathVariable UUID id) {
        return service.details(id);
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    SaleService.SaleDetails confirm(@Valid @RequestBody ConfirmRequest input) {
        return service.confirm(
                new ConfirmSaleCommand(
                        input.requestId(),
                        input.items().stream()
                                .map(
                                        item ->
                                                new ConfirmSaleCommand.Line(
                                                        item.productId(), item.quantity()))
                                .toList(),
                        new PaymentRequest(
                                input.paymentMethod(),
                                input.cashReceived(),
                                input.paymentReference(),
                                input.externallyConfirmed()),
                        input.expectedTotal()));
    }

    @PostMapping("/{id}/cancel")
    SaleService.SaleDetails cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
