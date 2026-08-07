CREATE TABLE work_order_comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_work_order_comments_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_work_order_comments_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT chk_work_order_comments_content_not_blank CHECK (CHAR_LENGTH(TRIM(content)) > 0)
);

CREATE INDEX idx_work_order_comments_work_order_time ON work_order_comments(work_order_id, created_at, id);
CREATE INDEX idx_work_order_comments_author_id ON work_order_comments(author_id);
