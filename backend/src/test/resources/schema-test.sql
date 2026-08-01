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

CREATE TABLE IF NOT EXISTS internal_barcode_sequences (
    sequence_name VARCHAR(32) PRIMARY KEY,
    next_value BIGINT NOT NULL
);

MERGE INTO internal_barcode_sequences (sequence_name, next_value)
KEY (sequence_name) VALUES ('PRODUCT', 1);

CREATE TABLE IF NOT EXISTS invoice_sequences (
    sequence_name VARCHAR(32) PRIMARY KEY,
    next_value BIGINT NOT NULL
);

MERGE INTO invoice_sequences (sequence_name, next_value)
KEY (sequence_name) VALUES ('INVOICE', 1);

CREATE TABLE IF NOT EXISTS purchase_sequences (
    sequence_name VARCHAR(32) PRIMARY KEY,
    next_value BIGINT NOT NULL
);

MERGE INTO purchase_sequences (sequence_name, next_value)
KEY (sequence_name) VALUES ('PURCHASE', 1);

CREATE TABLE IF NOT EXISTS purchase_return_sequences (
    sequence_name VARCHAR(32) PRIMARY KEY,
    next_value BIGINT NOT NULL
);

MERGE INTO purchase_return_sequences (sequence_name, next_value)
KEY (sequence_name) VALUES ('PURCHASE_RETURN', 1);

CREATE TABLE IF NOT EXISTS sale_return_sequences (
    sequence_name VARCHAR(32) PRIMARY KEY,
    next_value BIGINT NOT NULL
);

MERGE INTO sale_return_sequences (sequence_name, next_value)
KEY (sequence_name) VALUES ('SALE_RETURN', 1);
