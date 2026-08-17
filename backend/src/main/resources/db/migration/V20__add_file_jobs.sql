CREATE TABLE file_jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(60) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL,
    original_filename VARCHAR(255) NULL,
    result_file_path VARCHAR(500) NULL,
    error_report_path VARCHAR(500) NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    filter_json TEXT NULL,
    error_message VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    CONSTRAINT fk_file_jobs_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE INDEX idx_file_jobs_created_by ON file_jobs(created_by, created_at, id);
CREATE INDEX idx_file_jobs_type_status ON file_jobs(type, status, created_at, id);
