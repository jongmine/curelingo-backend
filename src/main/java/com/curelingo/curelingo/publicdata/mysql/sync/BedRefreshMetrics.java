package com.curelingo.curelingo.publicdata.mysql.sync;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class BedRefreshMetrics {
    public BedRefreshMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        Gauge.builder("curelingo.bed.last.success.timestamp", jdbc,
                db -> number(db, "SELECT COALESCE(MAX(UNIX_TIMESTAMP(last_success_at)), 0) FROM bed_refresh_state"))
                .register(registry);
        Gauge.builder("curelingo.bed.refresh.failed", jdbc,
                db -> number(db, "SELECT COALESCE(MAX(status = 'FAILED'), 0) FROM bed_refresh_state"))
                .register(registry);
        Gauge.builder("curelingo.bed.source.rows", jdbc,
                db -> number(db, "SELECT COALESCE(MAX(source_rows), 0) FROM bed_refresh_state"))
                .register(registry);
        Gauge.builder("curelingo.egen.daily.requests", jdbc, db -> {
            Number result = db.queryForObject("SELECT COALESCE(SUM(requests),0) FROM egen_daily_budget WHERE budget_date = ?",
                    Number.class, LocalDate.now(ZoneId.of("Asia/Seoul")));
            return result.doubleValue();
        }).register(registry);
    }

    private static double number(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Number.class).doubleValue();
    }
}
