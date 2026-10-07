package com.stockflow.reports.infrastructure;

import com.stockflow.reports.domain.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReportRepository implements ReportRepository {
    private final JdbcTemplate jdbc;

    public JdbcReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SALES_FILTER =
            " s.business_id = ? and s.status = 'CONFIRMED' and s.created_at >= ? and s.created_at < ?";
    private static final String ITEMS_JOIN =
            " from sales s join sale_items i on i.business_id = s.business_id and i.sale_id = s.id where ";

    public ReportSummary summarize(
            UUID businessId, LocalDate from, LocalDate to, ZoneId zone, boolean monthly) {
        var start = Timestamp.from(from.atStartOfDay(zone).toInstant());
        var end = Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant());
        Object[] params = {businessId, start, end};
        var revenue =
                jdbc.queryForObject(
                        "select coalesce(sum(s.total),0) from sales s where " + SALES_FILTER,
                        BigDecimal.class,
                        params);
        long count =
                jdbc.queryForObject(
                        "select count(*) from sales s where " + SALES_FILTER, Long.class, params);
        long units =
                jdbc.queryForObject(
                        "select coalesce(sum(i.quantity),0)" + ITEMS_JOIN + SALES_FILTER,
                        Long.class,
                        params);
        long low =
                jdbc.queryForObject(
                        "select count(*) from products where business_id = ? and active and stock <= minimum_stock",
                        Long.class,
                        businessId);
        String topProduct = top("i.product_id", "i.product_name_snapshot", params);
        String topCategory =
                top(
                        "coalesce(i.category_id_snapshot::text, 'legacy:' || i.category_name_snapshot)",
                        "i.category_name_snapshot",
                        params);
        var hours =
                jdbc.query(
                        "select extract(hour from s.created_at at time zone ?)::int as hour, sum(s.total) as total, count(*) as sales from sales s where "
                                + SALES_FILTER
                                + " group by hour order by hour",
                        (rs, row) ->
                                new ReportSummary.HourlyTotal(
                                        rs.getInt("hour"),
                                        rs.getBigDecimal("total"),
                                        rs.getLong("sales")),
                        zone.getId(),
                        businessId,
                        start,
                        end);
        Integer peak =
                hours.stream()
                        .max(Comparator.comparingLong(ReportSummary.HourlyTotal::sales))
                        .map(ReportSummary.HourlyTotal::hour)
                        .orElse(null);
        long exits =
                jdbc.queryForObject(
                        "select coalesce(sum(-quantity),0) from inventory_movements where business_id = ? and type = 'MANUAL_EXIT' and reference_id is not null and created_at >= ? and created_at < ?",
                        Long.class,
                        params);
        String bucket = monthly ? "month" : "day";
        var totals =
                jdbc.query(
                        "select date_trunc('"
                                + bucket
                                + "', s.created_at at time zone ?)::date as date, sum(s.total) as total,"
                                + " sum((select coalesce(sum(i.quantity),0) from sale_items i where i.business_id = s.business_id and i.sale_id = s.id)) as units"
                                + " from sales s where "
                                + SALES_FILTER
                                + " group by date order by date",
                        (rs, row) ->
                                new ReportSummary.DailyTotal(
                                        rs.getDate("date").toLocalDate().toString(),
                                        rs.getBigDecimal("total"),
                                        rs.getLong("units")),
                        zone.getId(),
                        businessId,
                        start,
                        end);
        var byDate = new HashMap<String, ReportSummary.DailyTotal>();
        totals.forEach(value -> byDate.put(value.date(), value));
        var series = new ArrayList<ReportSummary.DailyTotal>();
        for (var date = monthly ? from.withDayOfMonth(1) : from;
                !date.isAfter(to);
                date = monthly ? date.plusMonths(1) : date.plusDays(1)) {
            series.add(
                    byDate.getOrDefault(
                            date.toString(),
                            new ReportSummary.DailyTotal(date.toString(), BigDecimal.ZERO, 0)));
        }
        return new ReportSummary(
                revenue,
                count,
                units,
                low,
                topProduct,
                topCategory,
                peak,
                exits,
                from,
                to,
                series,
                hours);
    }

    private String top(String identifier, String nameColumn, Object[] params) {
        // Column is a private constant selected above; all user input remains parameterized.
        return jdbc
                .query(
                        "select (array_agg("
                                + nameColumn
                                + " order by s.created_at desc, s.id desc))[1] as name"
                                + ITEMS_JOIN
                                + SALES_FILTER
                                + " group by "
                                + identifier
                                + " order by sum(i.quantity) desc, name, "
                                + identifier
                                + " limit 1",
                        (rs, row) -> rs.getString("name"),
                        params)
                .stream()
                .findFirst()
                .orElse(null);
    }
}
