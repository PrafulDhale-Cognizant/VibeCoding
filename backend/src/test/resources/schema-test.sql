CREATE TABLE IF NOT EXISTS audit_events (
    id VARCHAR(36) PRIMARY KEY,
    actor_user_id VARCHAR(36),
    event_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(100),
    correlation_id VARCHAR(64),
    details VARCHAR(4000) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL
);
