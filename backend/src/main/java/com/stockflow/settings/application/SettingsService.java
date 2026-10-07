package com.stockflow.settings.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.settings.domain.*;
import com.stockflow.shared.application.AuditService;
import java.math.BigDecimal;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SettingsService {
    private final SettingsRepository settings;
    private final CurrentIdentity current;
    private final AuditService audit;

    public SettingsService(
            SettingsRepository settings, CurrentIdentity current, AuditService audit) {
        this.settings = settings;
        this.current = current;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public BusinessSettings get() {
        return settings.get(current.businessId());
    }

    public BusinessSettings update(
            String mode,
            boolean scanner,
            boolean alerts,
            boolean taxEnabled,
            BigDecimal taxRate,
            boolean receipt,
            String timezone) {
        current.requireManager();
        ZoneId.of(timezone);
        var result =
                settings.save(
                        new BusinessSettings(
                                current.businessId(),
                                mode,
                                scanner,
                                alerts,
                                taxEnabled,
                                taxRate,
                                receipt,
                                timezone));
        audit.record(
                current.businessId(),
                current.userId(),
                "SETTINGS_UPDATED",
                "settings",
                current.businessId());
        return result;
    }
}
