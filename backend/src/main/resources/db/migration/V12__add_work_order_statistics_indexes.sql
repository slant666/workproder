CREATE INDEX idx_work_order_status_transitions_status_action_work_order
    ON work_order_status_transitions(new_status, action, work_order_id);