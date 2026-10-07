package com.stockflow.inventory.presentation;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.inventory.application.*;
import com.stockflow.inventory.domain.*;
import com.stockflow.shared.domain.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final ManualMovementService inventory;
    private final InventoryExitService exits;
    private final InventoryQueries queries;
    private final CurrentIdentity current;

    public InventoryController(
            ManualMovementService inventory,
            InventoryExitService exits,
            InventoryQueries queries,
            CurrentIdentity current) {
        this.inventory = inventory;
        this.exits = exits;
        this.queries = queries;
        this.current = current;
    }

    public record MovementRequest(
            @NotNull UUID requestId,
            @NotNull UUID productId,
            @NotNull @Pattern(regexp = "PURCHASE_ENTRY|MANUAL_ENTRY|MANUAL_EXIT|ADJUSTMENT")
                    String type,
            @Min(0) @Max(1000000) int quantity) {}

    public record ExitLineRequest(@NotNull UUID productId, @Min(1) @Max(1000000) int quantity) {}

    public record ExitRequest(
            @NotNull UUID requestId,
            @NotEmpty @Size(max = 100) List<@Valid ExitLineRequest> items) {}

    @GetMapping
    Page<InventoryQueries.MovementView> list(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.list(current.businessId(), productId, type, page, size);
    }

    @PostMapping
    InventoryMovement manual(@Valid @RequestBody MovementRequest input) {
        return inventory.confirm(
                input.requestId(),
                input.productId(),
                MovementType.valueOf(input.type()),
                input.quantity());
    }

    @PostMapping("/exits")
    InventoryExitService.ExitResult exit(@Valid @RequestBody ExitRequest input) {
        return exits.confirm(
                input.requestId(),
                input.items().stream()
                        .map(
                                item ->
                                        new InventoryExitService.ExitLine(
                                                item.productId(), item.quantity()))
                        .toList());
    }
}
