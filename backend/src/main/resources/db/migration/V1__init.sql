CREATE TABLE app_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    metadata_key VARCHAR(100) NOT NULL UNIQUE,
    metadata_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_metadata (metadata_key, metadata_value)
VALUES ('schema_version', '1');
