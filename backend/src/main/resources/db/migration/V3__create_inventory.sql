CREATE TABLE product_units (
    unit_code VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    display_name VARCHAR(40) NOT NULL,
    symbol VARCHAR(12) NOT NULL,
    decimal_allowed BOOLEAN NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO product_units (unit_code, display_name, symbol, decimal_allowed) VALUES
    ('PIECE', 'Piece', 'pc', FALSE),
    ('KILOGRAM', 'Kilogram', 'kg', TRUE),
    ('GRAM', 'Gram', 'g', TRUE),
    ('LITRE', 'Litre', 'L', TRUE),
    ('MILLILITRE', 'Millilitre', 'ml', TRUE),
    ('PACKET', 'Packet', 'pkt', FALSE),
    ('BOX', 'Box', 'box', FALSE),
    ('DOZEN', 'Dozen', 'doz', FALSE);

CREATE TABLE categories (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_categories_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE products (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    receipt_name VARCHAR(80) NOT NULL,
    sku VARCHAR(64),
    category_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    unit_code VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    hsn_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin,
    gst_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    purchase_cost DECIMAL(19,2) NOT NULL,
    selling_price DECIMAL(19,2) NOT NULL,
    minimum_stock_level DECIMAL(19,3) NOT NULL DEFAULT 0.000,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_unit FOREIGN KEY (unit_code) REFERENCES product_units (unit_code),
    CONSTRAINT chk_products_gst_rate CHECK (gst_rate BETWEEN 0.00 AND 100.00),
    CONSTRAINT chk_products_purchase_cost CHECK (purchase_cost >= 0.00),
    CONSTRAINT chk_products_selling_price CHECK (selling_price >= 0.00),
    CONSTRAINT chk_products_minimum_stock CHECK (minimum_stock_level >= 0.000)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_products_name ON products (name);
CREATE INDEX idx_products_category_active ON products (category_id, active);
CREATE INDEX idx_products_stock_alert ON products (active, minimum_stock_level);

CREATE TABLE product_barcodes (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    product_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    barcode_value VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    internal_barcode BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_product_barcodes_product UNIQUE (product_id),
    CONSTRAINT uq_product_barcodes_value UNIQUE (barcode_value),
    CONSTRAINT fk_product_barcodes_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE inventory_balances (
    product_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    quantity DECIMAL(19,3) NOT NULL DEFAULT 0.000,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_inventory_balances_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT chk_inventory_balance_non_negative CHECK (quantity >= 0.000)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE stock_transactions (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    product_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    transaction_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quantity_delta DECIMAL(19,3) NOT NULL,
    balance_after DECIMAL(19,3) NOT NULL,
    reason_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reference_type VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin,
    reference_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin,
    notes VARCHAR(500),
    actor_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_stock_transactions_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_transactions_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT chk_stock_transaction_type CHECK (
        transaction_type IN ('OPENING', 'ADJUSTMENT', 'PURCHASE', 'PURCHASE_RETURN', 'SALE', 'SALE_RETURN', 'CORRECTION')
    ),
    CONSTRAINT chk_stock_reason_code CHECK (
        reason_code IN ('OPENING_STOCK', 'PHYSICAL_COUNT', 'DAMAGE', 'EXPIRY', 'THEFT_LOSS', 'FOUND_STOCK', 'DATA_CORRECTION', 'OTHER')
    ),
    CONSTRAINT chk_stock_quantity_delta CHECK (quantity_delta <> 0.000),
    CONSTRAINT chk_stock_balance_after CHECK (balance_after >= 0.000)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_stock_transactions_product_time
    ON stock_transactions (product_id, occurred_at DESC);
CREATE INDEX idx_stock_transactions_reference
    ON stock_transactions (reference_type, reference_id);

CREATE TABLE internal_barcode_sequences (
    sequence_name VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    next_value BIGINT UNSIGNED NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO internal_barcode_sequences (sequence_name, next_value)
VALUES ('PRODUCT', 1);
