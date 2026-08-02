ALTER TABLE invoices
    ADD COLUMN customer_gstin VARCHAR(15) NULL AFTER prices_include_gst;
