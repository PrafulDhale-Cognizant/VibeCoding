ALTER TABLE shop_profiles
    DROP CHECK chk_shop_a4_invoice_template;

ALTER TABLE shop_profiles
    ADD CONSTRAINT chk_shop_a4_invoice_template
        CHECK (a4_invoice_template IN ('MODERN', 'CLASSIC', 'MINIMAL', 'ELEGANT_GOLD'));
