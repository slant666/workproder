ALTER TABLE users
    ADD COLUMN email VARCHAR(160) NULL,
    ADD COLUMN email_verified_at TIMESTAMP NULL;

CREATE UNIQUE INDEX uk_users_email ON users(email);

CREATE TABLE email_verification_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens(user_id, expires_at, used_at);

CREATE TABLE password_reset_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id, expires_at, used_at);

CREATE TABLE email_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recipient_user_id BIGINT NULL,
    to_email VARCHAR(160) NOT NULL,
    type VARCHAR(80) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    related_work_order_id BIGINT NULL,
    dedupe_key VARCHAR(220) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(500) NULL,
    sent_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_email_outbox_recipient_user FOREIGN KEY (recipient_user_id) REFERENCES users(id),
    CONSTRAINT fk_email_outbox_work_order FOREIGN KEY (related_work_order_id) REFERENCES work_orders(id),
    CONSTRAINT uk_email_outbox_dedupe UNIQUE (dedupe_key)
);

CREATE INDEX idx_email_outbox_delivery ON email_outbox(status, next_attempt_at, id);
