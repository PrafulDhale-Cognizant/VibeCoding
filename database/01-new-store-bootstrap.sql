-- Simplified Billing - new store database bootstrap
-- Target: MySQL 8.4 LTS
-- Run once as a MySQL administrator before the application is started.
--
-- This script creates only the database container. The application account is
-- intentionally created separately because its password must be unique for
-- each customer and must never be committed to this repository.
--
-- After this script succeeds:
--   1. create the restricted billing_app account as shown in Customer-Setup-Guide.md;
--   2. configure BILLING_DB_* and BILLING_JWT_SECRET_BASE64 on the workstation;
--   3. start Simplified Billing;
--   4. Flyway will apply V1 through the latest migration automatically.

CREATE DATABASE IF NOT EXISTS billing
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

SELECT
    schema_name AS database_name,
    default_character_set_name AS character_set_name,
    default_collation_name AS collation_name
FROM information_schema.schemata
WHERE schema_name = 'billing';
