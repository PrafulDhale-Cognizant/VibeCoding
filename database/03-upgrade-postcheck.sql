-- Simplified Billing - post-upgrade verification
-- Target: MySQL 8.4 LTS
-- Run after the upgraded application reaches the login/setup screen.
-- This script is read-only. Current release schema target: Flyway V16.

SELECT VERSION() AS mysql_version,
       CURRENT_USER() AS connected_account,
       NOW(6) AS checked_at;

SELECT
    version AS current_flyway_version,
    description,
    installed_on,
    success
FROM billing.flyway_schema_history
WHERE version IS NOT NULL
ORDER BY CAST(version AS UNSIGNED) DESC
LIMIT 1;

SELECT
    CASE
        WHEN EXISTS (
            SELECT 1
            FROM billing.flyway_schema_history
            WHERE version = '16' AND success = 1
        ) AND NOT EXISTS (
            SELECT 1
            FROM billing.flyway_schema_history
            WHERE success = 0
        ) THEN 'PASS - schema is at V16 or newer with no failed migration'
        ELSE 'FAIL - inspect flyway_schema_history before using the application'
    END AS migration_check;

SELECT
    CASE
        WHEN COUNT(*) = 5 THEN 'PASS - current invoice and return columns are present'
        ELSE CONCAT('FAIL - only ', COUNT(*), ' of 5 expected columns are present')
    END AS release_column_check
FROM information_schema.columns
WHERE table_schema = 'billing'
  AND (
       (table_name = 'shop_profiles' AND column_name IN (
           'invoice_print_format', 'a4_invoice_template', 'thermal_receipt_template'
       ))
       OR (table_name = 'invoices' AND column_name = 'gst_applied')
       OR (table_name = 'sale_return_items' AND column_name = 'purchase_cost')
  );

SELECT
    CASE
        WHEN COUNT(*) = 2 THEN 'PASS - sales return archive indexes are present'
        ELSE CONCAT('FAIL - only ', COUNT(*), ' of 2 expected indexes are present')
    END AS return_index_check
FROM information_schema.statistics
WHERE table_schema = 'billing'
  AND table_name = 'sale_returns'
  AND index_name IN ('uq_sale_returns_number', 'idx_sale_returns_time');

SELECT 'invoices' AS business_object, COUNT(*) AS row_count FROM billing.invoices
UNION ALL
SELECT 'sale_returns', COUNT(*) FROM billing.sale_returns
UNION ALL
SELECT 'products', COUNT(*) FROM billing.products
UNION ALL
SELECT 'customers', COUNT(*) FROM billing.customers
UNION ALL
SELECT 'purchases', COUNT(*) FROM billing.purchases;
