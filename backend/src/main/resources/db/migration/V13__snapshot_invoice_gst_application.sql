ALTER TABLE invoices
    ADD COLUMN gst_applied BOOLEAN NOT NULL DEFAULT FALSE AFTER customer_gstin;

UPDATE invoices
SET gst_applied = TRUE
WHERE cgst_amount > 0.00 OR sgst_amount > 0.00 OR igst_amount > 0.00;
