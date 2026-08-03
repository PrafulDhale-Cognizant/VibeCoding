-- Simplified Billing - existing store upgrade preflight
-- Target: MySQL 8.4 LTS
-- Run as a read-only diagnostic before installing a newer application version.
-- This script does not change customer data or schema objects.

SELECT VERSION() AS mysql_version,
       @@hostname AS database_host,
       @@port AS database_port,
       NOW(6) AS checked_at;

SELECT
    CASE
        WHEN COUNT(*) = 1 THEN 'PASS'
        ELSE 'FAIL - billing database is missing'
    END AS database_check
FROM information_schema.schemata
WHERE schema_name = 'billing';

SELECT
    table_name,
    table_rows,
    ROUND((data_length + index_length) / 1024 / 1024, 2) AS approximate_size_mb
FROM information_schema.tables
WHERE table_schema = 'billing'
ORDER BY table_name;

-- This query is expected to fail only when the application has never started.
-- For an existing store, every row must have success = 1.
SELECT
    installed_rank,
    version,
    description,
    installed_on,
    execution_time,
    success
FROM billing.flyway_schema_history
ORDER BY installed_rank;

SELECT
    COUNT(*) AS failed_migration_count
FROM billing.flyway_schema_history
WHERE success = 0;

-- Capture these business counts with the upgrade ticket. They are useful when
-- comparing the store before and after an application upgrade.
SELECT 'invoices' AS business_object, COUNT(*) AS row_count FROM billing.invoices
UNION ALL
SELECT 'sale_returns', COUNT(*) FROM billing.sale_returns
UNION ALL
SELECT 'products', COUNT(*) FROM billing.products
UNION ALL
SELECT 'customers', COUNT(*) FROM billing.customers
UNION ALL
SELECT 'purchases', COUNT(*) FROM billing.purchases;
