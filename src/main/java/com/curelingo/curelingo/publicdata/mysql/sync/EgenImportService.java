package com.curelingo.curelingo.publicdata.mysql.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.curelingo.curelingo.publicdata.mysql.egen.EgenClient;
import com.curelingo.curelingo.publicdata.mysql.egen.EgenPage;
import com.curelingo.curelingo.publicdata.mysql.egen.EgenProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Service
public class EgenImportService {
    private static final List<String> DEPARTMENT_CODES = java.util.stream.IntStream.rangeClosed(1, 29)
            .mapToObj(number -> "D%03d".formatted(number))
            .toList();
    private static final DateTimeFormatter EGEN_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final EgenClient client;
    private final EgenProperties properties;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final SyncRunRepository runs;
    private final TransactionTemplate transactions;
    private int hospitalRows;
    private int departmentRows;
    private int bedRows;
    private long activeRunId;

    public EgenImportService(EgenClient client, EgenProperties properties, JdbcTemplate jdbc,
                             NamedParameterJdbcTemplate namedJdbc, SyncRunRepository runs,
                             TransactionTemplate transactions) {
        this.client = client;
        this.properties = properties;
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.runs = runs;
        this.transactions = transactions;
    }

    public long synchronize() {
        if (properties.pageSize() < 1 || properties.pageSize() > 1000) {
            throw new IllegalStateException("EGEN_PAGE_SIZE must be between 1 and 1000");
        }
        client.beginRun();
        hospitalRows = 0;
        departmentRows = 0;
        bedRows = 0;
        activeRunId = runs.start();
        try {
            clearStaging(activeRunId);
            syncHospitals();
            syncDepartments();
            syncBeds();
            verifySnapshot();
            publishSnapshot();
            return activeRunId;
        } catch (RuntimeException e) {
            try {
                clearStaging(activeRunId);
            } catch (RuntimeException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            runs.fail(activeRunId, client.requestCount(), hospitalRows, departmentRows, bedRows, safeReason(e));
            throw e;
        }
    }

    private void syncHospitals() {
        scanPages("hospitals", EgenClient.HOSPITAL_FULL_PATH, properties.pageSize(), Map.of(), (items) -> {
            for (JsonNode item : items) requireHospital(item);
            batchHospitals(items);
            hospitalRows += items.size();
            progress();
        });
        long staged = count("hospital_stage");
        if (staged != hospitalRows) {
            throw new IllegalStateException("Hospital source contains repeated hpid values: received=" + hospitalRows + ", unique=" + staged);
        }
    }

    private void syncDepartments() {
        for (String code : DEPARTMENT_CODES) {
            scanPages("department-" + code, EgenClient.DEPARTMENT_PATH, properties.pageSize(), Map.of("QD", code), (items) -> {
                batchDepartments(items, code);
                departmentRows += items.size();
                progress();
            });
        }
        long staged = count("department_stage");
        if (staged != departmentRows) {
            throw new IllegalStateException("Department source contains repeated hospital/code pairs: received=" + departmentRows + ", unique=" + staged);
        }
    }

    private void syncBeds() {
        int pageSize = Math.min(500, properties.pageSize());
        scanPages("beds", EgenClient.BED_STATUS_PATH, pageSize, Map.of(), (items) -> {
            batchBeds(items);
            bedRows += items.size();
            progress();
        });
        long staged = count("bed_stage");
        if (staged != bedRows) {
            throw new IllegalStateException("Bed source contains repeated hpid values: received=" + bedRows + ", unique=" + staged);
        }
    }

    private void scanPages(String feed, String path, int pageSize, Map<String, String> filters, PageConsumer consumer) {
        EgenPage first = fetchPage(feed, path, 1, pageSize, filters);
        int expectedTotal = first.totalCount();
        runs.addExpectedRows(activeRunId, feed, expectedTotal);
        int pageCount = (int) Math.ceil(expectedTotal / (double) pageSize);
        int received = 0;
        for (int pageNo = 1; pageNo <= Math.max(1, pageCount); pageNo++) {
            EgenPage page = pageNo == 1 ? first : fetchPage(feed, path, pageNo, pageSize, filters);
            if (page.totalCount() != expectedTotal) {
                throw new IllegalStateException("E-Gen totalCount changed during paging; code=" + filters.getOrDefault("QD", "all"));
            }
            if (page.items().isEmpty() && pageNo <= pageCount) {
                throw new IllegalStateException("E-Gen returned an empty page before the expected end");
            }
            if (page.items().size() > pageSize) {
                throw new IllegalStateException("E-Gen returned more rows than requested");
            }
            consumer.accept(page.items());
            received += page.items().size();
        }
        if (received != expectedTotal) {
            throw new IllegalStateException("E-Gen page count mismatch; expected=" + expectedTotal + ", received=" + received);
        }
    }

    private EgenPage fetchPage(String feed, String path, int pageNo, int pageSize, Map<String, String> filters) {
        runs.pageStarted(activeRunId, feed, pageNo, client.requestCount());
        EgenPage page = client.fetch(path, pageNo, pageSize, filters);
        runs.pageCompleted(activeRunId, client.requestCount());
        return page;
    }

    private void batchHospitals(List<JsonNode> items) {
        String sql = """
                INSERT INTO hospital_stage
                    (run_id, hpid, duty_name, duty_address, duty_name_en, duty_address_en, duty_etc, rnum,
                     hospital_type, emergency_room_open, telephone, emergency_telephone,
                     duty_time_1s, duty_time_1c, duty_time_2s, duty_time_2c,
                     duty_time_3s, duty_time_3c, duty_time_4s, duty_time_4c,
                     duty_time_5s, duty_time_5c, duty_time_6s, duty_time_6c,
                     duty_time_7s, duty_time_7c, duty_time_8s, duty_time_8c, latitude, longitude)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                AS incoming
                ON DUPLICATE KEY UPDATE duty_name = incoming.duty_name,
                    duty_address = incoming.duty_address, hospital_type = incoming.hospital_type,
                    duty_name_en = incoming.duty_name_en, duty_address_en = incoming.duty_address_en,
                    duty_etc = incoming.duty_etc,
                    rnum = incoming.rnum,
                    emergency_room_open = incoming.emergency_room_open, telephone = incoming.telephone,
                    emergency_telephone = incoming.emergency_telephone,
                    duty_time_1s = incoming.duty_time_1s, duty_time_1c = incoming.duty_time_1c,
                    duty_time_2s = incoming.duty_time_2s, duty_time_2c = incoming.duty_time_2c,
                    duty_time_3s = incoming.duty_time_3s, duty_time_3c = incoming.duty_time_3c,
                    duty_time_4s = incoming.duty_time_4s, duty_time_4c = incoming.duty_time_4c,
                    duty_time_5s = incoming.duty_time_5s, duty_time_5c = incoming.duty_time_5c,
                    duty_time_6s = incoming.duty_time_6s, duty_time_6c = incoming.duty_time_6c,
                    duty_time_7s = incoming.duty_time_7s, duty_time_7c = incoming.duty_time_7c,
                    duty_time_8s = incoming.duty_time_8s, duty_time_8c = incoming.duty_time_8c,
                    latitude = incoming.latitude,
                    longitude = incoming.longitude
                """;
        jdbc.batchUpdate(sql, items, items.size(), (statement, item) -> {
            statement.setLong(1, activeRunId);
            statement.setString(2, text(item, "hpid"));
            statement.setString(3, text(item, "dutyName"));
            statement.setString(4, textOrNull(item, "dutyAddr"));
            statement.setString(5, textOrNull(item, "dutyNameEn"));
            statement.setString(6, textOrNull(item, "dutyAddrEn"));
            statement.setString(7, textOrNull(item, "dutyEtc"));
            statement.setString(8, textOrNull(item, "rnum"));
            statement.setString(9, textOrNull(item, "dutyDivNam"));
            statement.setString(10, textOrNull(item, "dutyEryn"));
            statement.setString(11, textOrNull(item, "dutyTel1"));
            statement.setString(12, textOrNull(item, "dutyTel3"));
            int index = 13;
            for (int day = 1; day <= 8; day++) {
                statement.setString(index++, textOrNull(item, "dutyTime" + day + "s"));
                statement.setString(index++, textOrNull(item, "dutyTime" + day + "c"));
            }
            setDouble(statement, 29, coordinate(item, "wgs84Lat", -90.0, 90.0));
            setDouble(statement, 30, coordinate(item, "wgs84Lon", -180.0, 180.0));
        });
    }

    private void batchDepartments(List<JsonNode> items, String code) {
        String sql = """
                INSERT INTO department_stage (run_id, hpid, department_code, department_name)
                VALUES (?, ?, ?, ?)
                AS incoming
                ON DUPLICATE KEY UPDATE department_name = incoming.department_name
                """;
        jdbc.batchUpdate(sql, items, items.size(), (statement, item) -> {
            String hpid = text(item, "hpid");
            if (hpid.isBlank()) throw new IllegalStateException("E-Gen department row lacks hpid");
            statement.setLong(1, activeRunId);
            statement.setString(2, hpid);
            statement.setString(3, code);
            statement.setString(4, textOrNull(item, "dgidIdNam"));
        });
    }

    private void batchBeds(List<JsonNode> items) {
        String sql = """
                INSERT INTO bed_stage
                    (run_id, hpid, duty_name, duty_tel3, hvec, hvs01, hv28, hvs02, hvoc,
                     hvs22, hvgc, hvs38, hv9, hvs14, hv38, hvs21, hv60, hvs60,
                     source_updated_at, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                AS incoming
                ON DUPLICATE KEY UPDATE duty_name = incoming.duty_name, duty_tel3 = incoming.duty_tel3,
                    hvec = incoming.hvec, hvs01 = incoming.hvs01, hv28 = incoming.hv28,
                    hvs02 = incoming.hvs02, hvoc = incoming.hvoc, hvs22 = incoming.hvs22,
                    hvgc = incoming.hvgc, hvs38 = incoming.hvs38, hv9 = incoming.hv9,
                    hvs14 = incoming.hvs14, hv38 = incoming.hv38, hvs21 = incoming.hvs21,
                    hv60 = incoming.hv60, hvs60 = incoming.hvs60,
                    source_updated_at = incoming.source_updated_at, fetched_at = incoming.fetched_at
                """;
        jdbc.batchUpdate(sql, items, items.size(), (statement, item) -> {
            String hpid = text(item, "hpid");
            if (hpid.isBlank()) throw new IllegalStateException("E-Gen bed row lacks hpid");
            statement.setLong(1, activeRunId);
            statement.setString(2, hpid);
            statement.setString(3, textOrNull(item, "dutyName"));
            statement.setString(4, textOrNull(item, "dutyTel3"));
            String[] bedFields = {"hvec", "hvs01", "hv28", "hvs02", "hvoc", "hvs22", "hvgc",
                    "hvs38", "hv9", "hvs14", "hv38", "hvs21", "hv60", "hvs60"};
            for (int i = 0; i < bedFields.length; i++) {
                setInteger(statement, 5 + i, integer(item, bedFields[i]));
            }
            LocalDateTime sourceTime = sourceTime(item);
            statement.setObject(19, sourceTime);
            statement.setTimestamp(20, Timestamp.from(Instant.now()));
        });
    }

    private void verifySnapshot() {
        if (hospitalRows == 0 || departmentRows == 0) {
            throw new IllegalStateException("E-Gen returned an empty hospital or department snapshot");
        }
        long unmatchedDepartments = namedJdbc.queryForObject("""
                SELECT COUNT(*) FROM department_stage d
                 WHERE d.run_id = :runId
                   AND NOT EXISTS (SELECT 1 FROM hospital_stage h WHERE h.run_id = d.run_id AND h.hpid = d.hpid)
                """, new MapSqlParameterSource("runId", activeRunId), Long.class);
        long unmatchedBeds = namedJdbc.queryForObject("""
                SELECT COUNT(*) FROM bed_stage b
                 WHERE b.run_id = :runId
                   AND NOT EXISTS (SELECT 1 FROM hospital_stage h WHERE h.run_id = b.run_id AND h.hpid = b.hpid)
                """, new MapSqlParameterSource("runId", activeRunId), Long.class);
        if (unmatchedDepartments != 0 || unmatchedBeds != 0) {
            throw new IllegalStateException("hpid relationship check failed; department=" + unmatchedDepartments + ", beds=" + unmatchedBeds);
        }
    }

    private void publishSnapshot() {
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE hospital h
                    LEFT JOIN hospital_stage s ON s.run_id = ? AND s.hpid = h.hpid
                       SET h.active = (s.hpid IS NOT NULL)
                    """, activeRunId);
            jdbc.update("""
                    INSERT INTO hospital
                         (hpid, duty_name, duty_address, duty_name_en, duty_address_en, duty_etc, rnum,
                         hospital_type, emergency_room_open, telephone, emergency_telephone,
                         duty_time_1s, duty_time_1c, duty_time_2s, duty_time_2c,
                         duty_time_3s, duty_time_3c, duty_time_4s, duty_time_4c,
                         duty_time_5s, duty_time_5c, duty_time_6s, duty_time_6c,
                         duty_time_7s, duty_time_7c, duty_time_8s, duty_time_8c,
                         active, imported_at)
                    SELECT incoming.hpid, incoming.duty_name, incoming.duty_address,
                           incoming.duty_name_en, incoming.duty_address_en, incoming.duty_etc, incoming.rnum, incoming.hospital_type,
                           incoming.emergency_room_open, incoming.telephone, incoming.emergency_telephone,
                           incoming.duty_time_1s, incoming.duty_time_1c, incoming.duty_time_2s, incoming.duty_time_2c,
                           incoming.duty_time_3s, incoming.duty_time_3c, incoming.duty_time_4s, incoming.duty_time_4c,
                           incoming.duty_time_5s, incoming.duty_time_5c, incoming.duty_time_6s, incoming.duty_time_6c,
                           incoming.duty_time_7s, incoming.duty_time_7c, incoming.duty_time_8s, incoming.duty_time_8c,
                           TRUE, UTC_TIMESTAMP(6)
                      FROM (SELECT hpid, duty_name, duty_address, duty_name_en, duty_address_en, duty_etc, rnum,
                                   hospital_type, emergency_room_open, telephone, emergency_telephone,
                                   duty_time_1s, duty_time_1c, duty_time_2s, duty_time_2c,
                                   duty_time_3s, duty_time_3c, duty_time_4s, duty_time_4c,
                                   duty_time_5s, duty_time_5c, duty_time_6s, duty_time_6c,
                                   duty_time_7s, duty_time_7c, duty_time_8s, duty_time_8c
                              FROM hospital_stage WHERE run_id = ?) incoming
                    ON DUPLICATE KEY UPDATE duty_name = incoming.duty_name,
                        duty_address = incoming.duty_address, hospital_type = incoming.hospital_type,
                        duty_name_en = incoming.duty_name_en, duty_address_en = incoming.duty_address_en,
                        duty_etc = incoming.duty_etc,
                        rnum = incoming.rnum,
                        emergency_room_open = incoming.emergency_room_open, telephone = incoming.telephone,
                        emergency_telephone = incoming.emergency_telephone,
                        duty_time_1s = incoming.duty_time_1s, duty_time_1c = incoming.duty_time_1c,
                        duty_time_2s = incoming.duty_time_2s, duty_time_2c = incoming.duty_time_2c,
                        duty_time_3s = incoming.duty_time_3s, duty_time_3c = incoming.duty_time_3c,
                        duty_time_4s = incoming.duty_time_4s, duty_time_4c = incoming.duty_time_4c,
                        duty_time_5s = incoming.duty_time_5s, duty_time_5c = incoming.duty_time_5c,
                        duty_time_6s = incoming.duty_time_6s, duty_time_6c = incoming.duty_time_6c,
                        duty_time_7s = incoming.duty_time_7s, duty_time_7c = incoming.duty_time_7c,
                        duty_time_8s = incoming.duty_time_8s, duty_time_8c = incoming.duty_time_8c,
                        active = TRUE,
                        imported_at = UTC_TIMESTAMP(6)
                    """, activeRunId);

            jdbc.update("""
                    DELETE l FROM hospital_location l
                    LEFT JOIN hospital_stage s ON s.run_id = ? AND s.hpid = l.hpid
                    WHERE s.hpid IS NULL OR s.latitude IS NULL OR s.longitude IS NULL
                    """, activeRunId);
            jdbc.update("""
                    INSERT INTO hospital_location (hpid, location)
                    SELECT incoming.hpid, incoming.location
                      FROM (SELECT hpid,
                                   ST_GeomFromText(CONCAT('POINT(', longitude, ' ', latitude, ')'),
                                                   4326, 'axis-order=long-lat') AS location
                              FROM hospital_stage WHERE run_id = ?
                               AND latitude IS NOT NULL AND longitude IS NOT NULL) incoming
                    ON DUPLICATE KEY UPDATE location = incoming.location
                    """, activeRunId);

            jdbc.update("DELETE FROM hospital_department");
            jdbc.update("""
                    INSERT INTO hospital_department (hpid, department_code, department_name)
                    SELECT hpid, department_code, department_name
                      FROM department_stage WHERE run_id = ?
                    """, activeRunId);

            jdbc.update("""
                    INSERT INTO hospital_bed_status
                        (hpid, duty_name, duty_tel3, hvec, hvs01, hv28, hvs02, hvoc, hvs22,
                         hvgc, hvs38, hv9, hvs14, hv38, hvs21, hv60, hvs60, source_updated_at, fetched_at)
                    SELECT incoming.hpid, incoming.duty_name, incoming.duty_tel3, incoming.hvec,
                           incoming.hvs01, incoming.hv28, incoming.hvs02, incoming.hvoc, incoming.hvs22,
                           incoming.hvgc, incoming.hvs38, incoming.hv9, incoming.hvs14, incoming.hv38,
                           incoming.hvs21, incoming.hv60, incoming.hvs60,
                           incoming.source_updated_at, incoming.fetched_at
                      FROM (SELECT hpid, duty_name, duty_tel3, hvec, hvs01, hv28, hvs02, hvoc, hvs22,
                                   hvgc, hvs38, hv9, hvs14, hv38, hvs21, hv60, hvs60,
                                   source_updated_at, fetched_at
                              FROM bed_stage WHERE run_id = ?) incoming
                    ON DUPLICATE KEY UPDATE duty_name = incoming.duty_name, duty_tel3 = incoming.duty_tel3,
                        hvec = incoming.hvec, hvs01 = incoming.hvs01, hv28 = incoming.hv28,
                        hvs02 = incoming.hvs02, hvoc = incoming.hvoc, hvs22 = incoming.hvs22,
                        hvgc = incoming.hvgc, hvs38 = incoming.hvs38, hv9 = incoming.hv9,
                        hvs14 = incoming.hvs14, hv38 = incoming.hv38, hvs21 = incoming.hvs21,
                        hv60 = incoming.hv60, hvs60 = incoming.hvs60,
                        source_updated_at = incoming.source_updated_at, fetched_at = incoming.fetched_at
                    """, activeRunId);
            jdbc.update("""
                    DELETE b FROM hospital_bed_status b
                    LEFT JOIN bed_stage s ON s.run_id = ? AND s.hpid = b.hpid
                    WHERE s.hpid IS NULL
                    """, activeRunId);
            clearStaging(activeRunId);
            runs.succeed(activeRunId, client.requestCount(), hospitalRows, departmentRows, bedRows);
        });
    }

    private void clearStaging(long runId) {
        jdbc.update("DELETE FROM bed_stage WHERE run_id = ?", runId);
        jdbc.update("DELETE FROM department_stage WHERE run_id = ?", runId);
        jdbc.update("DELETE FROM hospital_stage WHERE run_id = ?", runId);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE run_id = ?", Long.class, activeRunId);
    }

    private void progress() {
        runs.progress(activeRunId, client.requestCount(), hospitalRows, departmentRows, bedRows);
    }

    private static void requireHospital(JsonNode item) {
        String hpid = text(item, "hpid");
        String name = text(item, "dutyName");
        if (hpid.isBlank() || name.isBlank()) throw new IllegalStateException("E-Gen hospital row lacks hpid or dutyName");
        coordinate(item, "wgs84Lat", -90.0, 90.0);
        coordinate(item, "wgs84Lon", -180.0, 180.0);
    }

    private static String text(JsonNode item, String field) {
        return item.path(field).asText("").trim();
    }

    private static String textOrNull(JsonNode item, String field) {
        String value = text(item, field);
        return value.isBlank() ? null : value;
    }

    private static Double coordinate(JsonNode item, String field, double min, double max) {
        String value = text(item, field);
        if (value.isBlank()) return null;
        try {
            double coordinate = Double.parseDouble(value);
            if (!Double.isFinite(coordinate) || coordinate < min || coordinate > max) {
                throw new IllegalStateException("E-Gen coordinate outside WGS84 range");
            }
            return coordinate;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(JsonNode item, String field) {
        String value = text(item, field);
        if (value.isBlank()) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime sourceTime(JsonNode item) {
        String value = text(item, "hvidate");
        if (value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, EGEN_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static void setDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.DOUBLE);
        else statement.setDouble(index, value);
    }

    private static void setInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.INTEGER);
        else statement.setInt(index, value);
    }

    private static String safeReason(RuntimeException error) {
        if (error instanceof com.curelingo.curelingo.publicdata.mysql.egen.EgenApiException apiError) return apiError.getMessage();
        if (error instanceof IllegalStateException) return error.getMessage();
        return error.getClass().getSimpleName();
    }

    @FunctionalInterface
    private interface PageConsumer {
        void accept(List<JsonNode> items);
    }
}
