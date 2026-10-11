package com.curelingo.curelingo.publicdata.mysql.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.curelingo.curelingo.publicdata.mysql.egen.EgenClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;

@Service
public class BedSyncService {
    private static final DateTimeFormatter SOURCE_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);
    private final BedSnapshotCollector collector;
    private final EgenClient client;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final SyncRunRepository runs;

    public BedSyncService(BedSnapshotCollector collector, EgenClient client, JdbcTemplate jdbc,
                          TransactionTemplate transaction, SyncRunRepository runs) {
        this.collector = collector;
        this.client = client;
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.runs = runs;
    }

    // Serialize all feed jobs in this JVM. Deploy only one collector process per database.
    public long synchronize() {
        synchronized (client) {
            return synchronizeExclusive();
        }
    }

    private long synchronizeExclusive() {
        long runId = runs.start();
        client.beginRun();
        jdbc.update("""
                INSERT INTO bed_refresh_state (id, last_attempt_at, status) VALUES (1, ?, 'RUNNING')
                ON DUPLICATE KEY UPDATE last_attempt_at = VALUES(last_attempt_at), status = 'RUNNING'
                """, Timestamp.from(Instant.now()));
        try {
            List<BedRow> rows = collector.collect().stream().map(BedSyncService::parse).toList();
            Timestamp observed = Timestamp.from(Instant.now());
            transaction.executeWithoutResult(status -> {
                for (BedRow row : rows) {
                    List<LocalDateTime> previous = jdbc.query(
                            "SELECT source_updated_at FROM hospital_bed_status WHERE hpid = ? FOR UPDATE",
                            (result, index) -> result.getObject(1, LocalDateTime.class), row.hpid());
                    if (!previous.isEmpty() && previous.get(0) != null
                            && (row.source() == null || row.source().isBefore(previous.get(0)))) {
                        throw new IllegalStateException("Bed source timestamp regressed");
                    }
                    // Unknown identifiers fail the transaction through the FK rather than disappearing silently.
                    jdbc.update("""
                        INSERT INTO hospital_bed_status
                          (hpid, duty_name, duty_tel3, hvec, hvs01, hv28, hvs02, hvoc, hvs22, hvgc, hvs38, hv9, hvs14, hv38, hvs21, hv60, hvs60, source_updated_at, fetched_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) AS incoming
                        ON DUPLICATE KEY UPDATE
                          duty_name = incoming.duty_name,
                          duty_tel3 = incoming.duty_tel3,
                          hvec = incoming.hvec,
                          hvs01 = incoming.hvs01,
                          hv28 = incoming.hv28,
                          hvs02 = incoming.hvs02,
                          hvoc = incoming.hvoc,
                          hvs22 = incoming.hvs22,
                          hvgc = incoming.hvgc,
                          hvs38 = incoming.hvs38,
                          hv9 = incoming.hv9,
                          hvs14 = incoming.hvs14,
                          hv38 = incoming.hv38,
                          hvs21 = incoming.hvs21,
                          hv60 = incoming.hv60,
                          hvs60 = incoming.hvs60,
                          source_updated_at = incoming.source_updated_at,
                          fetched_at = incoming.fetched_at
                        """, row.hpid(), row.name(), row.phone(), row.hvec(), row.hvs01(), row.hv28(), row.hvs02(), row.hvoc(), row.hvs22(), row.hvgc(), row.hvs38(), row.hv9(), row.hvs14(), row.hv38(), row.hvs21(), row.hv60(), row.hvs60(), row.source(), observed);
                }
                runs.succeed(runId, client.requestCount(), 0, 0, rows.size());
                jdbc.update("UPDATE bed_refresh_state SET status = 'SUCCEEDED', last_success_at = ?, source_rows = ? WHERE id = 1",
                        observed, rows.size());
            });
            return runId;
        } catch (RuntimeException failure) {
            // Do not persist exception messages that may contain request URLs or credentials.
            runs.fail(runId, client.requestCount(), 0, 0, 0, failure.getClass().getSimpleName());
            jdbc.update("UPDATE bed_refresh_state SET status = 'FAILED' WHERE id = 1");
            throw failure;
        }
    }

    private static BedRow parse(JsonNode item) {
        String source = item.path("hvidate").asText("").trim();
        LocalDateTime timestamp = source.isEmpty() ? null : LocalDateTime.parse(source, SOURCE_TIME);
        return new BedRow(item.path("hpid").asText().trim(), nullableText(item, "dutyName"),
                nullableText(item, "dutyTel3"), integer(item, "hvec"), integer(item, "hvs01"), integer(item, "hv28"), integer(item, "hvs02"), integer(item, "hvoc"), integer(item, "hvs22"), integer(item, "hvgc"), integer(item, "hvs38"), integer(item, "hv9"), integer(item, "hvs14"), integer(item, "hv38"), integer(item, "hvs21"), integer(item, "hv60"), integer(item, "hvs60"), timestamp);
    }

    private static String nullableText(JsonNode item, String field) {
        String value = item.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static Integer integer(JsonNode item, String name) {
        String value = item.path(name).asText("").trim();
        return value.isEmpty() ? null : Integer.valueOf(value);
    }

    private record BedRow(String hpid, String name, String phone, Integer hvec, Integer hvs01, Integer hv28, Integer hvs02, Integer hvoc, Integer hvs22, Integer hvgc, Integer hvs38, Integer hv9, Integer hvs14, Integer hv38, Integer hvs21, Integer hv60, Integer hvs60, LocalDateTime source) { }
}
