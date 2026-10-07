package com.stockflow.settings.presentation;

import com.stockflow.settings.application.SettingsService;
import com.stockflow.settings.domain.BusinessSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {
    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    public record SettingsRequest(
            @NotNull @Pattern(regexp = "POS|INVENTORY_ONLY") String operatingMode,
            boolean scannerEnabled,
            boolean stockAlerts,
            boolean taxEnabled,
            @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4)
                    BigDecimal taxRate,
            boolean generateReceipt,
            @NotBlank @Size(max = 80) String timezone) {}

    @GetMapping
    BusinessSettings get() {
        return service.get();
    }

    @PutMapping
    BusinessSettings update(@Valid @RequestBody SettingsRequest input) {
        return service.update(
                input.operatingMode(),
                input.scannerEnabled(),
                input.stockAlerts(),
                input.taxEnabled(),
                input.taxRate(),
                input.generateReceipt(),
                input.timezone());
    }
}
