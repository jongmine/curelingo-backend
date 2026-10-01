package com.curelingo.curelingo.egen.batch;

import com.curelingo.curelingo.publicdata.mysql.egen.EgenClient;
import com.curelingo.curelingo.publicdata.mysql.egen.EgenPage;
import com.curelingo.curelingo.publicdata.mysql.egen.EgenProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!mysql-sync")
public class AvailableBedsBatchJob {
    private static final DateTimeFormatter EGEN_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String[] BED_FIELDS = {
            "hvec", "hvs01", "hv28", "hvs02", "hvoc", "hvs22", "hvgc",
            "hvs38", "hv9", "hvs14", "hv38", "hvs21", "hv60", "hvs60"
    };
    private static final String UPSERT_SQL = """
            INSERT INTO hospital_bed_status
                (hpid, duty_name, duty_tel3, hvec, hvs01, hv28, hvs02, hvoc, hvs22,
                 hvgc, hvs38, hv9, hvs14, hv38, hvs21, hv60, hvs60, source_updated_at, fetched_at)
            SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
              FROM hospital WHERE hpid = ?
            ON DUPLICATE KEY UPDATE duty_name = VALUES(duty_name), duty_tel3 = VALUES(duty_tel3),
                hvec = VALUES(hvec), hvs01 = VALUES(hvs01), hv28 = VALUES(hv28),
                hvs02 = VALUES(hvs02), hvoc = VALUES(hvoc), hvs22 = VALUES(hvs22),
                hvgc = VALUES(hvgc), hvs38 = VALUES(hvs38), hv9 = VALUES(hv9),
                hvs14 = VALUES(hvs14), hv38 = VALUES(hv38), hvs21 = VALUES(hvs21),
                hv60 = VALUES(hv60), hvs60 = VALUES(hvs60),
                source_updated_at = VALUES(source_updated_at), fetched_at = VALUES(fetched_at)
            """;

    private final EgenClient egenClient;
    private final EgenProperties properties;
    private final JdbcTemplate jdbc;

    @PostConstruct
    public void onStartUp() {
        log.info("서버 시작 시 응급 병상 데이터 최초 수집 실행");
        fetchAndSaveAvailableBeds();
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void fetchAndSaveAvailableBeds() {
        try {
            int pageSize = Math.min(500, properties.pageSize());
            egenClient.beginRun();
            EgenPage first = egenClient.fetch(EgenClient.BED_STATUS_PATH, 1, pageSize, Map.of());
            int totalCount = first.totalCount();
            int pageCount = (int) Math.ceil(totalCount / (double) pageSize);
            int rows = savePage(first.items());
            for (int page = 2; page <= pageCount; page++) {
                EgenPage response = egenClient.fetch(EgenClient.BED_STATUS_PATH, page, pageSize, Map.of());
                if (response.totalCount() != totalCount) {
                    throw new IllegalStateException("E-Gen bed totalCount changed during paging");
                }
                rows += savePage(response.items());
            }
            log.info("응급 병상 데이터 수집 완료: sourceRows={}, apiRequests={}", rows, egenClient.requestCount());
        } catch (Exception e) {
            log.error("응급 병상 데이터 수집 실패", e);
        }
    }

    private int savePage(List<com.fasterxml.jackson.databind.JsonNode> items) {
        if (items.isEmpty()) return 0;
        jdbc.batchUpdate(UPSERT_SQL, items, items.size(), this::bindBedStatus);
        return items.size();
    }

    private void bindBedStatus(PreparedStatement statement, com.fasterxml.jackson.databind.JsonNode item)
            throws SQLException {
        String hpid = text(item, "hpid");
        if (hpid.isBlank()) throw new IllegalStateException("E-Gen bed row lacks hpid");
        statement.setString(1, hpid);
        statement.setString(2, textOrNull(item, "dutyName"));
        statement.setString(3, textOrNull(item, "dutyTel3"));
        for (int i = 0; i < BED_FIELDS.length; i++) setInteger(statement, 4 + i, integer(item, BED_FIELDS[i]));
        LocalDateTime sourceUpdatedAt = sourceTime(item);
        if (sourceUpdatedAt == null) statement.setNull(18, java.sql.Types.TIMESTAMP);
        else statement.setObject(18, sourceUpdatedAt);
        statement.setTimestamp(19, Timestamp.from(java.time.Instant.now()));
        statement.setString(20, hpid);
    }

    private static LocalDateTime sourceTime(com.fasterxml.jackson.databind.JsonNode item) {
        String value = text(item, "hvidate");
        if (value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, EGEN_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String text(com.fasterxml.jackson.databind.JsonNode item, String field) {
        return item.path(field).asText("").trim();
    }

    private static String textOrNull(com.fasterxml.jackson.databind.JsonNode item, String field) {
        String value = text(item, field);
        return value.isBlank() ? null : value;
    }

    private static Integer integer(com.fasterxml.jackson.databind.JsonNode item, String field) {
        String value = text(item, field);
        if (value.isBlank()) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void setInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.INTEGER);
        else statement.setInt(index, value);
    }
}
