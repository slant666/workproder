CREATE TABLE work_order_status_transitions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    old_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_work_order_status_transitions_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_work_order_status_transitions_actor FOREIGN KEY (actor_id) REFERENCES users(id)
);

CREATE INDEX idx_work_order_status_transitions_work_order_id ON work_order_status_transitions(work_order_id);
CREATE INDEX idx_work_order_status_transitions_created_at ON work_order_status_transitions(created_at);
