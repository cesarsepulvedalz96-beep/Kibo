package com.stockflow.reports.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.reports.domain.*;
import com.stockflow.settings.domain.SettingsRepository;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ReportService {
    private final ReportRepository reports;
    private final SettingsRepository settings;
    private final CurrentIdentity current;

    public ReportService(
            ReportRepository reports, SettingsRepository settings, CurrentIdentity current) {
        this.reports = reports;
        this.settings = settings;
        this.current = current;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReportSummary summary(String range) {
        var zone = ZoneId.of(settings.get(current.businessId()).timezone());
        var today = LocalDate.now(zone);
        var from =
                switch (range) {
                    case "WEEK" -> today.minusDays(6);
                    case "MONTH" -> today.withDayOfMonth(1);
                    case "YEAR" -> today.withDayOfYear(1);
                    default -> today;
                };
        return reports.summarize(current.businessId(), from, today, zone, range.equals("YEAR"));
    }
}
