CREATE TABLE work_order_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    old_handler_id BIGINT NULL,
    new_handler_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_work_order_assignments_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_work_order_assignments_old_handler FOREIGN KEY (old_handler_id) REFERENCES users(id),
    CONSTRAINT fk_work_order_assignments_new_handler FOREIGN KEY (new_handler_id) REFERENCES users(id),
    CONSTRAINT fk_work_order_assignments_assigned_by FOREIGN KEY (assigned_by) REFERENCES users(id)
);

CREATE INDEX idx_work_order_assignments_work_order_id ON work_order_assignments(work_order_id);
CREATE INDEX idx_work_order_assignments_created_at ON work_order_assignments(created_at);
