ALTER TABLE sale_return_items
    ADD COLUMN purchase_cost DECIMAL(19,2) NOT NULL AFTER disposition;

ALTER TABLE sale_return_items
    ADD CONSTRAINT chk_sale_return_purchase_cost CHECK (purchase_cost >= 0.00);
