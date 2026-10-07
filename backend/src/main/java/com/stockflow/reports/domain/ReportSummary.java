package com.stockflow.reports.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReportSummary(
        BigDecimal revenue,
        long saleCount,
        long unitsSold,
        long lowStockCount,
        String topProduct,
        String topCategory,
        Integer peakHour,
        long inventoryExits,
        LocalDate from,
        LocalDate to,
        List<DailyTotal> series,
        List<HourlyTotal> hours) {
    public record DailyTotal(String date, BigDecimal total, long units) {}

    public record HourlyTotal(int hour, BigDecimal total, long sales) {}
}
