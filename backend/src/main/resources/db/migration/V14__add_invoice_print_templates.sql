ALTER TABLE shop_profiles
    ADD COLUMN a4_invoice_template VARCHAR(16) NOT NULL DEFAULT 'MODERN' AFTER receipt_width,
    ADD COLUMN thermal_receipt_template VARCHAR(16) NOT NULL DEFAULT 'CLASSIC' AFTER a4_invoice_template;

ALTER TABLE shop_profiles
    ADD CONSTRAINT chk_shop_a4_invoice_template
        CHECK (a4_invoice_template IN ('MODERN', 'CLASSIC', 'MINIMAL')),
    ADD CONSTRAINT chk_shop_thermal_receipt_template
        CHECK (thermal_receipt_template IN ('CLASSIC', 'COMPACT', 'BORDERED'));
