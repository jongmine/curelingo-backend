package com.curelingo.curelingo.publicdata.mysql.sync;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class SyncRunRepository {
    private final JdbcTemplate jdbc;

    public SyncRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long start() {
        jdbc.update("INSERT INTO sync_run (status, started_at) VALUES ('RUNNING', ?)", Timestamp.from(Instant.now()));
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0 : id;
    }

    public void progress(long id, int apiRequests, int hospitalRows, int departmentRows, int bedRows) {
        jdbc.update("""
                UPDATE sync_run
                   SET api_requests = ?, hospital_rows = ?, department_rows = ?, bed_rows = ?
                 WHERE id = ? AND status = 'RUNNING'
                """, apiRequests, hospitalRows, departmentRows, bedRows, id);
    }

    public void pageStarted(long id, String feed, int page, int apiRequests) {
        jdbc.update("""
                UPDATE sync_run SET current_feed = ?, current_page = ?, api_requests = ?
                 WHERE id = ? AND status = 'RUNNING'
                """, feed, page, apiRequests, id);
    }

    public void pageCompleted(long id, int apiRequests) {
        jdbc.update("""
                UPDATE sync_run SET current_feed = NULL, current_page = NULL,
                       api_requests = ?, successful_pages = successful_pages + 1
                 WHERE id = ? AND status = 'RUNNING'
                """, apiRequests, id);
    }

    public void addExpectedRows(long id, String feed, int expectedRows) {
        String column = feed.startsWith("department-") ? "department_expected_rows"
                : "beds".equals(feed) ? "bed_expected_rows" : "hospital_expected_rows";
        String expression = feed.startsWith("department-") ? column + " + ?" : "?";
        jdbc.update("UPDATE sync_run SET " + column + " = " + expression + " WHERE id = ? AND status = 'RUNNING'",
                expectedRows, id);
    }

    public void succeed(long id, int apiRequests, int hospitalRows, int departmentRows, int bedRows) {
        jdbc.update("""
                UPDATE sync_run
                   SET status = 'SUCCEEDED', api_requests = ?, hospital_rows = ?, department_rows = ?,
                       bed_rows = ?, finished_at = ?, message = NULL, current_feed = NULL, current_page = NULL
                 WHERE id = ? AND status = 'RUNNING'
                """, apiRequests, hospitalRows, departmentRows, bedRows, Timestamp.from(Instant.now()), id);
    }

    public void fail(long id, int apiRequests, int hospitalRows, int departmentRows, int bedRows, String reason) {
        String safeReason = reason == null ? "Import failed" : reason.replaceAll("[\\r\\n]+", " ");
        if (safeReason.length() > 480) safeReason = safeReason.substring(0, 480);
        jdbc.update("""
                UPDATE sync_run
                   SET status = 'FAILED', api_requests = ?, hospital_rows = ?, department_rows = ?,
                       bed_rows = ?, finished_at = ?, message = ?, current_feed = NULL, current_page = NULL
                 WHERE id = ? AND status = 'RUNNING'
                """, apiRequests, hospitalRows, departmentRows, bedRows, Timestamp.from(Instant.now()), safeReason, id);
    }
}
