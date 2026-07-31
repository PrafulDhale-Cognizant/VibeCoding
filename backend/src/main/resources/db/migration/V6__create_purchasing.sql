ALTER TABLE stock_transactions DROP CHECK chk_stock_reason_code;
ALTER TABLE stock_transactions ADD CONSTRAINT chk_stock_reason_code CHECK (
    reason_code IN (
        'OPENING_STOCK', 'PHYSICAL_COUNT', 'DAMAGE', 'EXPIRY', 'THEFT_LOSS',
        'FOUND_STOCK', 'DATA_CORRECTION', 'PURCHASE', 'SALE', 'SALE_RETURN', 'OTHER'
    )
);

CREATE TABLE purchase_sequences (
    sequence_name VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    next_value BIGINT UNSIGNED NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO purchase_sequences (sequence_name, next_value) VALUES ('PURCHASE', 1);

CREATE TABLE suppliers (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(15) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    gstin VARCHAR(15) CHARACTER SET ascii COLLATE ascii_bin,
    address VARCHAR(500),
    notes VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_suppliers_phone UNIQUE (phone),
    CONSTRAINT uq_suppliers_gstin UNIQUE (gstin)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_suppliers_name ON suppliers (name);
CREATE INDEX idx_suppliers_active_name ON suppliers (active, name);

CREATE TABLE supplier_payable_balances (
    supplier_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    outstanding_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_supplier_balances_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE CASCADE,
    CONSTRAINT chk_supplier_balance_non_negative CHECK (outstanding_amount >= 0.00)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_supplier_balances_outstanding
    ON supplier_payable_balances (outstanding_amount);

CREATE TABLE purchases (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    purchase_number VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    supplier_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    supplier_name VARCHAR(150) NOT NULL,
    supplier_invoice_number VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin,
    invoice_date DATE NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prices_include_tax BOOLEAN NOT NULL,
    subtotal_amount DECIMAL(19,2) NOT NULL,
    tax_amount DECIMAL(19,2) NOT NULL,
    total_amount DECIMAL(19,2) NOT NULL,
    amount_paid DECIMAL(19,2) NOT NULL,
    outstanding_added DECIMAL(19,2) NOT NULL,
    payment_mode VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin,
    payment_reference VARCHAR(100),
    notes VARCHAR(500),
    actor_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_purchases_number UNIQUE (purchase_number),
    CONSTRAINT uq_purchases_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uq_supplier_invoice UNIQUE (supplier_id, supplier_invoice_number),
    CONSTRAINT fk_purchases_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_purchases_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT chk_purchase_status CHECK (status IN ('RECEIVED')),
    CONSTRAINT chk_purchase_payment_mode CHECK (
        payment_mode IS NULL OR payment_mode IN ('CASH', 'UPI', 'CARD', 'BANK_TRANSFER')
    ),
    CONSTRAINT chk_purchase_amounts CHECK (
        subtotal_amount >= 0.00 AND tax_amount >= 0.00 AND total_amount > 0.00
        AND amount_paid >= 0.00 AND outstanding_added >= 0.00
        AND amount_paid + outstanding_added = total_amount
    ),
    CONSTRAINT chk_purchase_payment_details CHECK (
        (amount_paid = 0.00 AND payment_mode IS NULL)
        OR (amount_paid > 0.00 AND payment_mode IS NOT NULL)
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_purchases_received_at ON purchases (received_at DESC);
CREATE INDEX idx_purchases_supplier_time ON purchases (supplier_id, received_at DESC);

CREATE TABLE purchase_items (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    purchase_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    line_number INT NOT NULL,
    product_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    unit_code VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quantity DECIMAL(19,3) NOT NULL,
    unit_cost DECIMAL(19,2) NOT NULL,
    gst_rate DECIMAL(5,2) NOT NULL,
    taxable_amount DECIMAL(19,2) NOT NULL,
    tax_amount DECIMAL(19,2) NOT NULL,
    line_total DECIMAL(19,2) NOT NULL,
    CONSTRAINT uq_purchase_items_line UNIQUE (purchase_id, line_number),
    CONSTRAINT fk_purchase_items_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_purchase_items_unit FOREIGN KEY (unit_code) REFERENCES product_units (unit_code),
    CONSTRAINT chk_purchase_item_quantity CHECK (quantity > 0.000),
    CONSTRAINT chk_purchase_item_amounts CHECK (
        unit_cost >= 0.00 AND gst_rate >= 0.00 AND taxable_amount >= 0.00
        AND tax_amount >= 0.00 AND line_total > 0.00
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_purchase_items_product ON purchase_items (product_id);

CREATE TABLE supplier_ledger_entries (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    supplier_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entry_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    balance_after DECIMAL(19,2) NOT NULL,
    purchase_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    idempotency_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin,
    payment_mode VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin,
    payment_reference VARCHAR(100),
    notes VARCHAR(500),
    actor_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_supplier_ledger_purchase UNIQUE (purchase_id),
    CONSTRAINT uq_supplier_ledger_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_supplier_ledger_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_supplier_ledger_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id),
    CONSTRAINT fk_supplier_ledger_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT chk_supplier_entry_type CHECK (entry_type IN ('PURCHASE_DUE', 'PAYMENT')),
    CONSTRAINT chk_supplier_payment_mode CHECK (
        payment_mode IS NULL OR payment_mode IN ('CASH', 'UPI', 'CARD', 'BANK_TRANSFER')
    ),
    CONSTRAINT chk_supplier_ledger_amounts CHECK (amount > 0.00 AND balance_after >= 0.00),
    CONSTRAINT chk_supplier_ledger_reference CHECK (
        (entry_type = 'PURCHASE_DUE' AND purchase_id IS NOT NULL AND idempotency_key IS NULL AND payment_mode IS NULL)
        OR (entry_type = 'PAYMENT' AND purchase_id IS NULL AND idempotency_key IS NOT NULL AND payment_mode IS NOT NULL)
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_supplier_ledger_supplier_time
    ON supplier_ledger_entries (supplier_id, occurred_at DESC);
