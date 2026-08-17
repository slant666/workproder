ALTER TABLE work_orders
    ADD COLUMN first_response_due_at TIMESTAMP NULL,
    ADD COLUMN resolution_due_at TIMESTAMP NULL,
    ADD COLUMN first_responded_at TIMESTAMP NULL,
    ADD COLUMN resolved_at TIMESTAMP NULL,
    ADD COLUMN sla_status VARCHAR(40) NOT NULL DEFAULT 'NORMAL';

CREATE INDEX idx_work_orders_sla_scan ON work_orders(status, sla_status, first_response_due_at, resolution_due_at);

CREATE TABLE work_order_sla_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_work_order_sla_events_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT uk_work_order_sla_event UNIQUE (work_order_id, event_type)
);

CREATE TABLE notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    type VARCHAR(60) NOT NULL,
    title VARCHAR(160) NOT NULL,
    content VARCHAR(500) NOT NULL,
    work_order_id BIGINT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users(id),
    CONSTRAINT fk_notifications_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id)
);

CREATE INDEX idx_notifications_recipient_read_created ON notifications(recipient_id, read_at, created_at, id);
CREATE INDEX idx_notifications_work_order ON notifications(work_order_id, id);
