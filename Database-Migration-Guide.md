# Database Migration and Store Upgrade Guide

This guide is for developers and support engineers preparing either a new customer database or an
upgrade of an existing Simplified Billing store. The supported database is MySQL 8.4 LTS.

## Migration model

The database is managed by Flyway inside the Spring Boot backend. Versioned files are located in:

```text
backend/src/main/resources/db/migration/
```

They are packaged inside `backend/target/billing-backend.jar`. Whenever the installed application
starts, Flyway reads `billing.flyway_schema_history`, validates checksums, and applies only versions
that have not run on that store.

Do not concatenate the migration files into one script and do not manually rerun `V1` through
`V16` against an existing store. Doing so bypasses Flyway history and can damage a customer
database. Never edit a versioned migration after it has been released.

## Current migration inventory

| Version | Purpose |
|---|---|
| V1 | Foundation settings and audit events |
| V2 | Shop profile, users, roles, and refresh tokens |
| V3 | Products, barcodes, inventory balances, and stock ledger |
| V4 | POS invoices, invoice lines, payments, and invoice numbering |
| V5 | Customers, Khata balances, and Khata ledger |
| V6 | Suppliers, purchases, supplier balances, and supplier ledger |
| V7 | Purchase returns and supplier-credit accounting |
| V8 | Sales returns, refunds, return numbering, and invoice return states |
| V9 | Purchase-cost snapshot on sales-return lines |
| V10 | Customer phone snapshot on invoice payments |
| V11 | Optional customer attachment for every payment mode |
| V12 | Customer GSTIN snapshot on invoices |
| V13 | Whether GST was applied to each invoice |
| V14 | A4 and thermal invoice-template selections |
| V15 | Default A4 or thermal invoice print format |
| V16 | Elegant Gold A4 invoice template |

The sales-return archive uses the unique return-number index and returned-date index created by
V8. No additional schema change is required for searching return invoices or reporting return
totals.

## Files supplied for a release

The `database` directory contains operational SQL scripts that do not replace Flyway:

- `01-new-store-bootstrap.sql` creates the empty `billing` database with the correct character set.
- `02-upgrade-preflight.sql` records the current version, failures, table sizes, and business counts.
- `03-upgrade-postcheck.sql` verifies V16, required columns, archive indexes, and business counts.

Keep these files beside the installer in the internal release package. Customers normally do not
need to run them themselves; an installer or support engineer should do so.

## New store database procedure

1. Install and start MySQL 8.4.
2. Open an administrator PowerShell window and locate `mysql.exe`:

   ```powershell
   $mysqlClient = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
   & $mysqlClient --version
   ```

3. Create the database:

   ```powershell
   & $mysqlClient -u root -p --execute="SOURCE C:/path/to/database/01-new-store-bootstrap.sql"
   ```

   MySQL `SOURCE` paths should use forward slashes.

4. Connect as the administrator and create a customer-specific application account:

   ```powershell
   & $mysqlClient -u root -p
   ```

   Then run the following SQL, replacing the example password with a long random password:

   ```sql
   CREATE USER 'billing_app'@'127.0.0.1'
       IDENTIFIED BY 'replace-with-a-unique-random-password';
   GRANT ALL PRIVILEGES ON billing.* TO 'billing_app'@'127.0.0.1';
   FLUSH PRIVILEGES;
   ```

5. Configure the workstation variables described in `Customer-Setup-Guide.md`.
6. Start Simplified Billing. The first startup applies V1 through V16 in numeric order.
7. Wait for the first-run shop setup screen. Do not terminate the application during migration.
8. Run the postcheck script and confirm all checks report `PASS`.

## Existing store upgrade procedure

1. Confirm the new installer came from the approved release location and has a valid signature.
2. In Simplified Billing, create an encrypted backup and copy the `.sbk` file to another drive.
3. Close the application and confirm no `Simplified Billing` or backend Java process remains.
4. Run the preflight script and save its output:

   ```powershell
   $mysqlClient = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
   & $mysqlClient -u billing_app -p --table --execute="SOURCE C:/path/to/database/02-upgrade-preflight.sql" |
       Tee-Object -FilePath .\upgrade-preflight.txt
   ```

5. Stop if `failed_migration_count` is not zero. Do not delete rows from
   `flyway_schema_history`; investigate the failed version first.
6. Run the newer Windows installer over the existing installation.
7. Start the application once and allow Flyway to finish. Future releases will apply only versions
   newer than the store's current successful version.
8. Run the postcheck script and compare the five business counts with the preflight output:

   ```powershell
   & $mysqlClient -u billing_app -p --table --execute="SOURCE C:/path/to/database/03-upgrade-postcheck.sql" |
       Tee-Object -FilePath .\upgrade-postcheck.txt
   ```

9. Sign in and smoke-test product search, one test invoice, invoice reprint, return search, reports,
   and backup creation.

## Failure and rollback procedure

If startup fails during an upgrade:

1. Do not repeatedly reinstall, edit migration SQL, or modify `flyway_schema_history` manually.
2. Save the backend log and a support diagnostics export.
3. Record the failing Flyway version and error message.
4. If customer operation must resume immediately, restore the pre-upgrade `.sbk` backup using the
   application's startup recovery flow or the approved support procedure.
5. Escalate the saved log, preflight output, installer version, and database backup to development.

V9 has a specific rerunnable recovery path in the application because MySQL DDL auto-commits. Do
not generalize that repair to other migration versions.

## Adding a future migration

For every schema or reference-data change:

1. Find the highest released version and create exactly one next file, for example
   `V17__short_description.sql`.
2. Make the change forward-only; preserve financial, invoice, stock, return, and ledger history.
3. Use MySQL 8.4 syntax and deterministic data updates.
4. Add or update repository/service tests affected by the new schema.
5. Test both paths on disposable MySQL databases: blank to latest, and previous release to latest.
6. Run `mvn -f backend/pom.xml clean verify` and confirm the coverage gate passes.
7. Package the JAR and verify the migration file exists inside it:

   ```powershell
   jar tf .\backend\target\billing-backend.jar |
       Select-String "BOOT-INF/classes/db/migration/V"
   ```

8. Never change the migration after the installer has been distributed. Fix later defects with a
   new incremented migration.
