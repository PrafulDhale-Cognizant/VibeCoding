ALTER TABLE stock_transactions DROP CHECK chk_stock_reason_code;
ALTER TABLE stock_transactions ADD CONSTRAINT chk_stock_reason_code CHECK (
    reason_code IN (
        'OPENING_STOCK', 'PHYSICAL_COUNT', 'DAMAGE', 'EXPIRY', 'THEFT_LOSS',
        'FOUND_STOCK', 'DATA_CORRECTION', 'PURCHASE', 'PURCHASE_RETURN',
        'SALE', 'SALE_RETURN', 'OTHER'
    )
);

CREATE TABLE purchase_return_sequences (
    sequence_name VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    next_value BIGINT UNSIGNED NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO purchase_return_sequences (sequence_name, next_value)
VALUES ('PURCHASE_RETURN', 1);

ALTER TABLE purchase_items
    ADD COLUMN returned_quantity DECIMAL(19,3) NOT NULL DEFAULT 0.000 AFTER quantity,
    ADD CONSTRAINT chk_purchase_item_returned_quantity
        CHECK (returned_quantity >= 0.000 AND returned_quantity <= quantity);

ALTER TABLE supplier_payable_balances
    ADD COLUMN credit_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00 AFTER outstanding_amount,
    ADD CONSTRAINT chk_supplier_credit_non_negative CHECK (credit_amount >= 0.00);

CREATE TABLE purchase_returns (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    return_number VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purchase_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    supplier_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    supplier_name VARCHAR(150) NOT NULL,
    return_date DATE NOT NULL,
    reason VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subtotal_amount DECIMAL(19,2) NOT NULL,
    tax_amount DECIMAL(19,2) NOT NULL,
    total_amount DECIMAL(19,2) NOT NULL,
    payable_reduction DECIMAL(19,2) NOT NULL,
    credit_added DECIMAL(19,2) NOT NULL,
    notes VARCHAR(500),
    actor_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    returned_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_purchase_returns_number UNIQUE (return_number),
    CONSTRAINT uq_purchase_returns_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_purchase_returns_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id),
    CONSTRAINT fk_purchase_returns_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_purchase_returns_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT chk_purchase_return_reason CHECK (
        reason IN ('DAMAGED', 'EXPIRED', 'WRONG_ITEM', 'QUALITY_ISSUE', 'EXCESS_STOCK', 'OTHER')
    ),
    CONSTRAINT chk_purchase_return_amounts CHECK (
        subtotal_amount >= 0.00 AND tax_amount >= 0.00 AND total_amount > 0.00
        AND payable_reduction >= 0.00 AND credit_added >= 0.00
        AND payable_reduction + credit_added = total_amount
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_purchase_returns_date ON purchase_returns (return_date DESC);
CREATE INDEX idx_purchase_returns_supplier_date
    ON purchase_returns (supplier_id, return_date DESC);
CREATE INDEX idx_purchase_returns_purchase
    ON purchase_returns (purchase_id, returned_at DESC);

CREATE TABLE purchase_return_items (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    purchase_return_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purchase_item_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
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
    CONSTRAINT uq_purchase_return_items_line UNIQUE (purchase_return_id, line_number),
    CONSTRAINT fk_purchase_return_items_return
        FOREIGN KEY (purchase_return_id) REFERENCES purchase_returns (id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_return_items_purchase_item
        FOREIGN KEY (purchase_item_id) REFERENCES purchase_items (id),
    CONSTRAINT fk_purchase_return_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_purchase_return_items_unit FOREIGN KEY (unit_code) REFERENCES product_units (unit_code),
    CONSTRAINT chk_purchase_return_item_quantity CHECK (quantity > 0.000),
    CONSTRAINT chk_purchase_return_item_amounts CHECK (
        unit_cost >= 0.00 AND gst_rate >= 0.00 AND taxable_amount >= 0.00
        AND tax_amount >= 0.00 AND line_total > 0.00
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_purchase_return_items_product
    ON purchase_return_items (product_id);

ALTER TABLE supplier_ledger_entries
    ADD COLUMN purchase_return_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
        AFTER purchase_id,
    ADD COLUMN credit_balance_after DECIMAL(19,2) NOT NULL DEFAULT 0.00
        AFTER balance_after,
    ADD CONSTRAINT uq_supplier_ledger_purchase_return UNIQUE (purchase_return_id),
    ADD CONSTRAINT fk_supplier_ledger_purchase_return
        FOREIGN KEY (purchase_return_id) REFERENCES purchase_returns (id),
    ADD CONSTRAINT chk_supplier_ledger_credit_balance
        CHECK (credit_balance_after >= 0.00);

ALTER TABLE supplier_ledger_entries DROP CHECK chk_supplier_entry_type;
ALTER TABLE supplier_ledger_entries ADD CONSTRAINT chk_supplier_entry_type CHECK (
    entry_type IN ('PURCHASE_DUE', 'PURCHASE_RETURN', 'PAYMENT')
);

ALTER TABLE supplier_ledger_entries DROP CHECK chk_supplier_ledger_reference;
ALTER TABLE supplier_ledger_entries ADD CONSTRAINT chk_supplier_ledger_reference CHECK (
    (entry_type = 'PURCHASE_DUE' AND purchase_id IS NOT NULL
        AND purchase_return_id IS NULL AND idempotency_key IS NULL AND payment_mode IS NULL)
    OR (entry_type = 'PURCHASE_RETURN' AND purchase_id IS NULL
        AND purchase_return_id IS NOT NULL AND idempotency_key IS NULL AND payment_mode IS NULL)
    OR (entry_type = 'PAYMENT' AND purchase_id IS NULL
        AND purchase_return_id IS NULL AND idempotency_key IS NOT NULL AND payment_mode IS NOT NULL)
);
