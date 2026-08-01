-- MySQL DDL auto-commits, so keep this migration safe to rerun after a failed
-- first attempt. Existing return rows must be backfilled before NOT NULL is
-- enforced.
ALTER TABLE sale_return_items
    ADD COLUMN IF NOT EXISTS purchase_cost DECIMAL(19,2) NULL AFTER disposition;

UPDATE sale_return_items returned_item
JOIN invoice_items source_item ON source_item.id = returned_item.invoice_item_id
SET returned_item.purchase_cost = source_item.purchase_cost
WHERE returned_item.purchase_cost IS NULL;

ALTER TABLE sale_return_items
    MODIFY COLUMN purchase_cost DECIMAL(19,2) NOT NULL AFTER disposition;
