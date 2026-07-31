CREATE TABLE customers (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(15) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    notes VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_customers_phone UNIQUE (phone)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_customers_name ON customers (name);
CREATE INDEX idx_customers_active_name ON customers (active, name);

CREATE TABLE customer_credit_balances (
    customer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    outstanding_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_customer_balances_customer
        FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT chk_customer_balance_non_negative CHECK (outstanding_amount >= 0.00)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_customer_balances_outstanding
    ON customer_credit_balances (outstanding_amount);

CREATE TABLE khata_ledger_entries (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    customer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entry_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    balance_after DECIMAL(19,2) NOT NULL,
    invoice_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    idempotency_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin,
    payment_mode VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin,
    payment_reference VARCHAR(100),
    notes VARCHAR(500),
    actor_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_khata_ledger_invoice UNIQUE (invoice_id),
    CONSTRAINT uq_khata_ledger_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_khata_ledger_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_khata_ledger_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_khata_ledger_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT chk_khata_entry_type CHECK (entry_type IN ('CREDIT_SALE', 'SETTLEMENT')),
    CONSTRAINT chk_khata_payment_mode CHECK (
        payment_mode IS NULL OR payment_mode IN ('CASH', 'UPI', 'CARD')
    ),
    CONSTRAINT chk_khata_amount_positive CHECK (amount > 0.00),
    CONSTRAINT chk_khata_balance_non_negative CHECK (balance_after >= 0.00),
    CONSTRAINT chk_khata_reference CHECK (
        (entry_type = 'CREDIT_SALE' AND invoice_id IS NOT NULL AND idempotency_key IS NULL)
        OR (entry_type = 'SETTLEMENT' AND invoice_id IS NULL AND idempotency_key IS NOT NULL)
    ),
    CONSTRAINT chk_khata_settlement_mode CHECK (
        (entry_type = 'CREDIT_SALE' AND payment_mode IS NULL)
        OR (entry_type = 'SETTLEMENT' AND payment_mode IS NOT NULL)
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_khata_ledger_customer_time
    ON khata_ledger_entries (customer_id, occurred_at DESC);

ALTER TABLE payments DROP CHECK chk_payment_mode;
ALTER TABLE payments ADD COLUMN customer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin;
ALTER TABLE payments ADD COLUMN customer_name VARCHAR(150);
ALTER TABLE payments ADD CONSTRAINT fk_payments_customer
    FOREIGN KEY (customer_id) REFERENCES customers (id);
ALTER TABLE payments ADD CONSTRAINT chk_payment_mode CHECK (
    payment_mode IN ('CASH', 'UPI', 'CARD', 'UDHAAR')
);
ALTER TABLE payments ADD CONSTRAINT chk_payment_customer CHECK (
    (payment_mode = 'UDHAAR' AND customer_id IS NOT NULL AND customer_name IS NOT NULL)
    OR (payment_mode <> 'UDHAAR' AND customer_id IS NULL AND customer_name IS NULL)
);
