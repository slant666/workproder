CREATE TABLE work_order_idempotency_keys (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    creator_id BIGINT NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    work_order_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_work_order_idempotency_creator FOREIGN KEY (creator_id) REFERENCES users(id),
    CONSTRAINT fk_work_order_idempotency_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT uk_work_order_idempotency_creator_key UNIQUE (creator_id, idempotency_key)
);

CREATE INDEX idx_work_order_idempotency_work_order ON work_order_idempotency_keys(work_order_id);
