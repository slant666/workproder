CREATE TABLE user_management_audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    action VARCHAR(60) NOT NULL,
    field_name VARCHAR(60) NOT NULL,
    old_value TEXT NULL,
    new_value TEXT NULL,
    details_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_management_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT fk_user_management_audit_logs_target_user FOREIGN KEY (target_user_id) REFERENCES users(id)
);

CREATE INDEX idx_user_management_audit_logs_target_time ON user_management_audit_logs(target_user_id, created_at, id);
CREATE INDEX idx_user_management_audit_logs_actor_id ON user_management_audit_logs(actor_id);