CREATE TABLE sale_return_sequences (
    sequence_name VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    next_value BIGINT UNSIGNED NOT NULL
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO sale_return_sequences (sequence_name, next_value) VALUES ('SALE_RETURN', 1);

ALTER TABLE invoices DROP CHECK chk_invoice_status;
ALTER TABLE invoices ADD CONSTRAINT chk_invoice_status CHECK (
    status IN ('COMPLETED', 'PARTIALLY_RETURNED', 'RETURNED', 'CANCELLED')
);

CREATE TABLE sale_returns (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    return_number VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    invoice_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    return_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(500) NOT NULL,
    subtotal_amount DECIMAL(19,2) NOT NULL,
    discount_amount DECIMAL(19,2) NOT NULL,
    taxable_amount DECIMAL(19,2) NOT NULL,
    cgst_amount DECIMAL(19,2) NOT NULL,
    sgst_amount DECIMAL(19,2) NOT NULL,
    igst_amount DECIMAL(19,2) NOT NULL,
    total_amount DECIMAL(19,2) NOT NULL,
    actor_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    returned_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_sale_returns_number UNIQUE (return_number),
    CONSTRAINT uq_sale_returns_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_sale_returns_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_sale_returns_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT chk_sale_return_type CHECK (return_type IN ('RETURN', 'CANCELLATION')),
    CONSTRAINT chk_sale_return_amounts CHECK (
        subtotal_amount >= 0 AND discount_amount >= 0 AND taxable_amount >= 0
        AND cgst_amount >= 0 AND sgst_amount >= 0 AND igst_amount >= 0 AND total_amount > 0
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_sale_returns_invoice_time ON sale_returns (invoice_id, returned_at DESC);
CREATE INDEX idx_sale_returns_time ON sale_returns (returned_at DESC);

CREATE TABLE sale_return_items (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    sale_return_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    invoice_item_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    line_number INT NOT NULL,
    product_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    unit_code VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quantity DECIMAL(19,3) NOT NULL,
    disposition VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    gst_rate DECIMAL(5,2) NOT NULL,
    gross_amount DECIMAL(19,2) NOT NULL,
    discount_amount DECIMAL(19,2) NOT NULL,
    taxable_amount DECIMAL(19,2) NOT NULL,
    cgst_amount DECIMAL(19,2) NOT NULL,
    sgst_amount DECIMAL(19,2) NOT NULL,
    igst_amount DECIMAL(19,2) NOT NULL,
    line_total DECIMAL(19,2) NOT NULL,
    CONSTRAINT uq_sale_return_item_line UNIQUE (sale_return_id, invoice_item_id),
    CONSTRAINT fk_sale_return_items_return FOREIGN KEY (sale_return_id) REFERENCES sale_returns (id),
    CONSTRAINT fk_sale_return_items_invoice_item FOREIGN KEY (invoice_item_id) REFERENCES invoice_items (id),
    CONSTRAINT fk_sale_return_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sale_return_items_unit FOREIGN KEY (unit_code) REFERENCES product_units (unit_code),
    CONSTRAINT chk_sale_return_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_sale_return_disposition CHECK (disposition IN ('SALEABLE', 'DAMAGED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_sale_return_items_source ON sale_return_items (invoice_item_id);

CREATE TABLE refund_records (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    sale_return_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    refund_mode VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    refund_reference VARCHAR(100),
    customer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    recorded_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_refunds_return FOREIGN KEY (sale_return_id) REFERENCES sale_returns (id),
    CONSTRAINT fk_refunds_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT chk_refund_mode CHECK (refund_mode IN ('CASH', 'UPI', 'CARD', 'UDHAAR')),
    CONSTRAINT chk_refund_amount CHECK (amount > 0),
    CONSTRAINT chk_refund_customer CHECK (
        (refund_mode = 'UDHAAR' AND customer_id IS NOT NULL)
        OR (refund_mode <> 'UDHAAR' AND customer_id IS NULL)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE khata_ledger_entries DROP CHECK chk_khata_entry_type;
ALTER TABLE khata_ledger_entries DROP CHECK chk_khata_reference;
ALTER TABLE khata_ledger_entries DROP CHECK chk_khata_settlement_mode;
ALTER TABLE khata_ledger_entries ADD INDEX idx_khata_ledger_invoice (invoice_id);
ALTER TABLE khata_ledger_entries DROP INDEX uq_khata_ledger_invoice;
ALTER TABLE khata_ledger_entries ADD COLUMN sale_return_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin;
ALTER TABLE khata_ledger_entries ADD CONSTRAINT uq_khata_ledger_sale_return UNIQUE (sale_return_id);
ALTER TABLE khata_ledger_entries ADD CONSTRAINT fk_khata_ledger_sale_return
    FOREIGN KEY (sale_return_id) REFERENCES sale_returns (id);
ALTER TABLE khata_ledger_entries ADD CONSTRAINT chk_khata_entry_type CHECK (
    entry_type IN ('CREDIT_SALE', 'SETTLEMENT', 'SALE_RETURN', 'CANCELLATION')
);
ALTER TABLE khata_ledger_entries ADD CONSTRAINT chk_khata_reference CHECK (
    (entry_type = 'CREDIT_SALE' AND invoice_id IS NOT NULL AND idempotency_key IS NULL AND sale_return_id IS NULL)
    OR (entry_type = 'SETTLEMENT' AND invoice_id IS NULL AND idempotency_key IS NOT NULL AND sale_return_id IS NULL)
    OR (entry_type IN ('SALE_RETURN', 'CANCELLATION') AND invoice_id IS NOT NULL AND idempotency_key IS NULL AND sale_return_id IS NOT NULL)
);
ALTER TABLE khata_ledger_entries ADD CONSTRAINT chk_khata_settlement_mode CHECK (
    (entry_type = 'SETTLEMENT' AND payment_mode IS NOT NULL)
    OR (entry_type <> 'SETTLEMENT' AND payment_mode IS NULL)
);
