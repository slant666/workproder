CREATE TABLE work_order_attachments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(120) NOT NULL UNIQUE,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_work_order_attachments_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_work_order_attachments_uploader FOREIGN KEY (uploader_id) REFERENCES users(id)
);

CREATE INDEX idx_work_order_attachments_work_order_time ON work_order_attachments(work_order_id, created_at, id);
CREATE INDEX idx_work_order_attachments_uploader_id ON work_order_attachments(uploader_id);
