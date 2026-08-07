CREATE TABLE work_order_operation_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(60) NOT NULL,
    field_name VARCHAR(60) NULL,
    old_value TEXT NULL,
    new_value TEXT NULL,
    details_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_work_order_operation_logs_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_work_order_operation_logs_actor FOREIGN KEY (actor_id) REFERENCES users(id)
);

CREATE INDEX idx_work_order_operation_logs_work_order_time ON work_order_operation_logs(work_order_id, created_at, id);
CREATE INDEX idx_work_order_operation_logs_actor_id ON work_order_operation_logs(actor_id);
