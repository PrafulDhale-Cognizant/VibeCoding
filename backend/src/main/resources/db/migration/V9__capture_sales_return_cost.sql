-- MySQL DDL auto-commits, so keep this migration safe to rerun after a failed
-- first attempt. MySQL does not support ADD COLUMN IF NOT EXISTS on every
-- supported version, therefore use metadata-driven dynamic SQL.
SET @add_purchase_cost_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE sale_return_items ADD COLUMN purchase_cost DECIMAL(19,2) NULL AFTER disposition',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sale_return_items'
      AND column_name = 'purchase_cost'
);

PREPARE add_purchase_cost_statement FROM @add_purchase_cost_sql;
EXECUTE add_purchase_cost_statement;
DEALLOCATE PREPARE add_purchase_cost_statement;

UPDATE sale_return_items returned_item
JOIN invoice_items source_item ON source_item.id = returned_item.invoice_item_id
SET returned_item.purchase_cost = source_item.purchase_cost;

ALTER TABLE sale_return_items
    MODIFY COLUMN purchase_cost DECIMAL(19,2) NOT NULL AFTER disposition;
