ALTER TABLE shop_profiles
    ADD COLUMN invoice_print_format VARCHAR(16) NOT NULL DEFAULT 'THERMAL' AFTER receipt_width;

ALTER TABLE shop_profiles
    ADD CONSTRAINT chk_shop_invoice_print_format
        CHECK (invoice_print_format IN ('A4', 'THERMAL'));
