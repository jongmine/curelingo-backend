-- Existing database only: apply once before deploying the new emergency query.
ALTER TABLE hospital ADD INDEX idx_hospital_emergency_active (emergency_room_open, active, hpid);
CREATE TABLE IF NOT EXISTS egen_daily_budget (
    budget_date DATE NOT NULL PRIMARY KEY,
    requests INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;


CREATE TABLE IF NOT EXISTS bed_refresh_state (
    id TINYINT NOT NULL PRIMARY KEY,
    last_success_at TIMESTAMP(6) NULL,
    last_attempt_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    source_rows INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
