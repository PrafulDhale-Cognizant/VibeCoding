ALTER TABLE stock_transactions DROP CHECK chk_stock_reason_code;

ALTER TABLE stock_transactions ADD CONSTRAINT chk_stock_reason_code CHECK (
    reason_code IN (
        'OPENING_STOCK', 'PHYSICAL_COUNT', 'DAMAGE', 'EXPIRY', 'THEFT_LOSS',
        'FOUND_STOCK', 'DATA_CORRECTION', 'SALE', 'SALE_RETURN', 'OTHER'
    )
);

CREATE TABLE invoice_sequences (
    sequence_name VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    next_value BIGINT UNSIGNED NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO invoice_sequences (sequence_name, next_value) VALUES ('INVOICE', 1);

CREATE TABLE invoices (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    invoice_number VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cashier_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tax_mode VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prices_include_gst BOOLEAN NOT NULL,
    subtotal_amount DECIMAL(19,2) NOT NULL,
    line_discount_amount DECIMAL(19,2) NOT NULL,
    bill_discount_amount DECIMAL(19,2) NOT NULL,
    taxable_amount DECIMAL(19,2) NOT NULL,
    cgst_amount DECIMAL(19,2) NOT NULL,
    sgst_amount DECIMAL(19,2) NOT NULL,
    igst_amount DECIMAL(19,2) NOT NULL,
    round_off_amount DECIMAL(19,2) NOT NULL,
    total_amount DECIMAL(19,2) NOT NULL,
    notes VARCHAR(500),
    completed_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_invoices_number UNIQUE (invoice_number),
    CONSTRAINT uq_invoices_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_invoices_cashier FOREIGN KEY (cashier_user_id) REFERENCES users (id),
    CONSTRAINT chk_invoice_status CHECK (status IN ('COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_invoice_tax_mode CHECK (tax_mode IN ('INTRA_STATE', 'INTER_STATE')),
    CONSTRAINT chk_invoice_non_negative CHECK (
        subtotal_amount >= 0.00 AND line_discount_amount >= 0.00
        AND bill_discount_amount >= 0.00 AND taxable_amount >= 0.00
        AND cgst_amount >= 0.00 AND sgst_amount >= 0.00
        AND igst_amount >= 0.00 AND total_amount >= 0.00
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_invoices_completed_at ON invoices (completed_at DESC);
CREATE INDEX idx_invoices_cashier_time ON invoices (cashier_user_id, completed_at DESC);

CREATE TABLE invoice_items (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    invoice_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    line_number INT NOT NULL,
    product_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    receipt_name VARCHAR(80) NOT NULL,
    barcode VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    unit_code VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quantity DECIMAL(19,3) NOT NULL,
    purchase_cost DECIMAL(19,2) NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    gst_rate DECIMAL(5,2) NOT NULL,
    gross_amount DECIMAL(19,2) NOT NULL,
    line_discount_amount DECIMAL(19,2) NOT NULL,
    bill_discount_amount DECIMAL(19,2) NOT NULL,
    taxable_amount DECIMAL(19,2) NOT NULL,
    cgst_amount DECIMAL(19,2) NOT NULL,
    sgst_amount DECIMAL(19,2) NOT NULL,
    igst_amount DECIMAL(19,2) NOT NULL,
    line_total DECIMAL(19,2) NOT NULL,
    CONSTRAINT uq_invoice_items_line UNIQUE (invoice_id, line_number),
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT fk_invoice_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_invoice_items_unit FOREIGN KEY (unit_code) REFERENCES product_units (unit_code),
    CONSTRAINT chk_invoice_item_quantity CHECK (quantity > 0.000),
    CONSTRAINT chk_invoice_item_non_negative CHECK (
        purchase_cost >= 0.00 AND unit_price >= 0.00 AND gst_rate >= 0.00
        AND gross_amount >= 0.00 AND line_discount_amount >= 0.00
        AND bill_discount_amount >= 0.00 AND taxable_amount >= 0.00
        AND cgst_amount >= 0.00 AND sgst_amount >= 0.00
        AND igst_amount >= 0.00 AND line_total >= 0.00
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_invoice_items_product ON invoice_items (product_id);

CREATE TABLE payments (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    invoice_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payment_mode VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    tendered_amount DECIMAL(19,2),
    change_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    payment_reference VARCHAR(100),
    recorded_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT chk_payment_mode CHECK (payment_mode IN ('CASH', 'UPI', 'CARD')),
    CONSTRAINT chk_payment_amount CHECK (amount > 0.00),
    CONSTRAINT chk_payment_change CHECK (change_amount >= 0.00)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

