package com.stockflow.settings.infrastructure;

import com.stockflow.settings.domain.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSettingsRepository implements SettingsRepository {
    private final JdbcTemplate jdbc;

    public JdbcSettingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BusinessSettings get(UUID businessId) {
        return jdbc.queryForObject(
                "select * from business_settings where business_id = ?",
                (rs, row) ->
                        new BusinessSettings(
                                businessId,
                                rs.getString("operating_mode"),
                                rs.getBoolean("scanner_enabled"),
                                rs.getBoolean("stock_alerts"),
                                rs.getBoolean("tax_enabled"),
                                rs.getBigDecimal("tax_rate"),
                                rs.getBoolean("generate_receipt"),
                                rs.getString("timezone")),
                businessId);
    }

    public BusinessSettings save(BusinessSettings settings) {
        jdbc.update(
                """
                insert into business_settings(business_id, operating_mode, scanner_enabled, stock_alerts,
                    tax_enabled, tax_rate, generate_receipt, timezone) values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(business_id) do update set operating_mode = excluded.operating_mode,
                    scanner_enabled = excluded.scanner_enabled, stock_alerts = excluded.stock_alerts,
                    tax_enabled = excluded.tax_enabled, tax_rate = excluded.tax_rate,
                    generate_receipt = excluded.generate_receipt, timezone = excluded.timezone
                """,
                settings.businessId(),
                settings.operatingMode(),
                settings.scannerEnabled(),
                settings.stockAlerts(),
                settings.taxEnabled(),
                settings.taxRate(),
                settings.generateReceipt(),
                settings.timezone());
        return settings;
    }
}
