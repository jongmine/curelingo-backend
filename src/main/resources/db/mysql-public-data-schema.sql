CREATE TABLE IF NOT EXISTS sync_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    status VARCHAR(16) NOT NULL,
    api_requests INT NOT NULL DEFAULT 0,
    hospital_rows INT NOT NULL DEFAULT 0,
    department_rows INT NOT NULL DEFAULT 0,
    bed_rows INT NOT NULL DEFAULT 0,
    hospital_expected_rows INT NOT NULL DEFAULT 0,
    department_expected_rows INT NOT NULL DEFAULT 0,
    bed_expected_rows INT NOT NULL DEFAULT 0,
    successful_pages INT NOT NULL DEFAULT 0,
    current_feed VARCHAR(32) NULL,
    current_page INT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    finished_at TIMESTAMP(6) NULL,
    message VARCHAR(512) NULL,
    PRIMARY KEY (id),
    KEY idx_sync_run_status_started (status, started_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS hospital (
    hpid VARCHAR(32) NOT NULL,
    duty_name VARCHAR(200) NOT NULL,
    duty_address VARCHAR(500) NULL,
    duty_name_en VARCHAR(200) NULL,
    duty_address_en VARCHAR(500) NULL,
    duty_etc VARCHAR(500) NULL,
    rnum VARCHAR(32) NULL,
    hospital_type VARCHAR(100) NULL,
    emergency_room_open VARCHAR(8) NULL,
    telephone VARCHAR(32) NULL,
    emergency_telephone VARCHAR(32) NULL,
    duty_time_1s VARCHAR(4) NULL,
    duty_time_1c VARCHAR(4) NULL,
    duty_time_2s VARCHAR(4) NULL,
    duty_time_2c VARCHAR(4) NULL,
    duty_time_3s VARCHAR(4) NULL,
    duty_time_3c VARCHAR(4) NULL,
    duty_time_4s VARCHAR(4) NULL,
    duty_time_4c VARCHAR(4) NULL,
    duty_time_5s VARCHAR(4) NULL,
    duty_time_5c VARCHAR(4) NULL,
    duty_time_6s VARCHAR(4) NULL,
    duty_time_6c VARCHAR(4) NULL,
    duty_time_7s VARCHAR(4) NULL,
    duty_time_7c VARCHAR(4) NULL,
    duty_time_8s VARCHAR(4) NULL,
    duty_time_8c VARCHAR(4) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    imported_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (hpid),
    KEY idx_hospital_active_name (active, duty_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS hospital_location (
    hpid VARCHAR(32) NOT NULL,
    location POINT NOT NULL SRID 4326,
    PRIMARY KEY (hpid),
    SPATIAL INDEX idx_hospital_location (location),
    CONSTRAINT fk_hospital_location_hospital
        FOREIGN KEY (hpid) REFERENCES hospital (hpid) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS hospital_department (
    hpid VARCHAR(32) NOT NULL,
    department_code VARCHAR(4) NOT NULL,
    department_name VARCHAR(100) NULL,
    PRIMARY KEY (hpid, department_code),
    KEY idx_hospital_department_lookup (department_code, hpid),
    CONSTRAINT fk_hospital_department_hospital
        FOREIGN KEY (hpid) REFERENCES hospital (hpid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS hospital_bed_status (
    hpid VARCHAR(32) NOT NULL,
    duty_name VARCHAR(200) NULL,
    duty_tel3 VARCHAR(32) NULL,
    hvec INT NULL,
    hvs01 INT NULL,
    hv28 INT NULL,
    hvs02 INT NULL,
    hvoc INT NULL,
    hvs22 INT NULL,
    hvgc INT NULL,
    hvs38 INT NULL,
    hv9 INT NULL,
    hvs14 INT NULL,
    hv38 INT NULL,
    hvs21 INT NULL,
    hv60 INT NULL,
    hvs60 INT NULL,
    source_updated_at DATETIME(6) NULL,
    fetched_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (hpid),
    KEY idx_bed_status_fetched (fetched_at),
    CONSTRAINT fk_hospital_bed_hospital
        FOREIGN KEY (hpid) REFERENCES hospital (hpid) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS hospital_stage (
    run_id BIGINT NOT NULL,
    hpid VARCHAR(32) NOT NULL,
    duty_name VARCHAR(200) NOT NULL,
    duty_address VARCHAR(500) NULL,
    duty_name_en VARCHAR(200) NULL,
    duty_address_en VARCHAR(500) NULL,
    duty_etc VARCHAR(500) NULL,
    rnum VARCHAR(32) NULL,
    hospital_type VARCHAR(100) NULL,
    emergency_room_open VARCHAR(8) NULL,
    telephone VARCHAR(32) NULL,
    emergency_telephone VARCHAR(32) NULL,
    duty_time_1s VARCHAR(4) NULL,
    duty_time_1c VARCHAR(4) NULL,
    duty_time_2s VARCHAR(4) NULL,
    duty_time_2c VARCHAR(4) NULL,
    duty_time_3s VARCHAR(4) NULL,
    duty_time_3c VARCHAR(4) NULL,
    duty_time_4s VARCHAR(4) NULL,
    duty_time_4c VARCHAR(4) NULL,
    duty_time_5s VARCHAR(4) NULL,
    duty_time_5c VARCHAR(4) NULL,
    duty_time_6s VARCHAR(4) NULL,
    duty_time_6c VARCHAR(4) NULL,
    duty_time_7s VARCHAR(4) NULL,
    duty_time_7c VARCHAR(4) NULL,
    duty_time_8s VARCHAR(4) NULL,
    duty_time_8c VARCHAR(4) NULL,
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    PRIMARY KEY (run_id, hpid),
    CONSTRAINT fk_hospital_stage_run FOREIGN KEY (run_id) REFERENCES sync_run (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS department_stage (
    run_id BIGINT NOT NULL,
    hpid VARCHAR(32) NOT NULL,
    department_code VARCHAR(4) NOT NULL,
    department_name VARCHAR(100) NULL,
    PRIMARY KEY (run_id, hpid, department_code),
    KEY idx_department_stage_lookup (run_id, department_code, hpid),
    CONSTRAINT fk_department_stage_run FOREIGN KEY (run_id) REFERENCES sync_run (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS bed_stage (
    run_id BIGINT NOT NULL,
    hpid VARCHAR(32) NOT NULL,
    duty_name VARCHAR(200) NULL,
    duty_tel3 VARCHAR(32) NULL,
    hvec INT NULL,
    hvs01 INT NULL,
    hv28 INT NULL,
    hvs02 INT NULL,
    hvoc INT NULL,
    hvs22 INT NULL,
    hvgc INT NULL,
    hvs38 INT NULL,
    hv9 INT NULL,
    hvs14 INT NULL,
    hv38 INT NULL,
    hvs21 INT NULL,
    hv60 INT NULL,
    hvs60 INT NULL,
    source_updated_at DATETIME(6) NULL,
    fetched_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (run_id, hpid),
    CONSTRAINT fk_bed_stage_run FOREIGN KEY (run_id) REFERENCES sync_run (id) ON DELETE CASCADE
) ENGINE=InnoDB;
