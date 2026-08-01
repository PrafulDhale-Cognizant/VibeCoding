ALTER TABLE payments
    ADD COLUMN customer_phone VARCHAR(20) NULL AFTER customer_name;

ALTER TABLE payments DROP CHECK chk_payment_customer;
ALTER TABLE payments ADD CONSTRAINT chk_payment_customer CHECK (
    (payment_mode <> 'UDHAAR' AND customer_id IS NULL AND customer_name IS NULL AND customer_phone IS NULL)
    OR (customer_id IS NOT NULL AND customer_name IS NOT NULL)
);
