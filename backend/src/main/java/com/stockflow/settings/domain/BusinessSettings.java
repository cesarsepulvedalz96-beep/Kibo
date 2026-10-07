package com.stockflow.settings.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record BusinessSettings(
        UUID businessId,
        String operatingMode,
        boolean scannerEnabled,
        boolean stockAlerts,
        boolean taxEnabled,
        BigDecimal taxRate,
        boolean generateReceipt,
        String timezone) {}
