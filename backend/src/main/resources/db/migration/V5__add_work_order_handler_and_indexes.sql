ALTER TABLE work_orders
    ADD COLUMN handler_id BIGINT NULL,
    ADD CONSTRAINT fk_work_orders_handler FOREIGN KEY (handler_id) REFERENCES users(id);

CREATE INDEX idx_work_orders_creator_id ON work_orders(creator_id);
CREATE INDEX idx_work_orders_handler_id ON work_orders(handler_id);
CREATE INDEX idx_work_orders_status ON work_orders(status);
CREATE INDEX idx_work_orders_priority ON work_orders(priority);
CREATE INDEX idx_work_orders_created_at ON work_orders(created_at);
