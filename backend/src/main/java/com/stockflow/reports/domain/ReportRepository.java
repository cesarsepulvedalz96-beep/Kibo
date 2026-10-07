package com.stockflow.reports.domain;

import java.time.*;
import java.util.UUID;

public interface ReportRepository {
    ReportSummary summarize(
            UUID businessId, LocalDate from, LocalDate to, ZoneId zone, boolean monthly);
}
