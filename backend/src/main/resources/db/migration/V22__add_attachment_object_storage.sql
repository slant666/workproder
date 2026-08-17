ALTER TABLE work_order_attachments
    ADD COLUMN storage_provider VARCHAR(30) NOT NULL DEFAULT 'local',
    ADD COLUMN bucket_name VARCHAR(120) NULL,
    ADD COLUMN object_key VARCHAR(500) NULL,
    ADD COLUMN deleted_at TIMESTAMP NULL;

UPDATE work_order_attachments
SET object_key = stored_filename
WHERE object_key IS NULL;

CREATE INDEX idx_work_order_attachments_storage ON work_order_attachments(storage_provider, bucket_name, object_key);
