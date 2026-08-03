# Simplified Billing Customer Installation and Setup Guide

This guide is for the technician installing Simplified Billing on a customer workstation and for
the store owner completing first-time setup. It covers a new installation, printers, backups, and
future upgrades.

## Important packaging note

The current Windows installer contains the desktop application and Spring Boot backend. It does
not currently install MySQL or a private Java runtime. Unless the release package explicitly says
otherwise, install Java 21 and MySQL 8.4 before running Simplified Billing.

Database passwords, JWT secrets, shop credentials, code-signing files, and backup passwords are
not included in the installer. Every customer must receive unique values.

## Customer workstation requirements

- 64-bit Windows 10 or Windows 11
- 8 GB RAM recommended
- At least 10 GB free disk space, plus capacity for database growth and backups
- Java 21 available from the Windows command line
- MySQL 8.4 LTS running locally
- An A4, 58 mm, or 80 mm printer supported by Windows, depending on the store
- Optional USB barcode scanner operating in keyboard/HID mode
- Optional barcode-label printer supported by Windows
- A second drive, network folder, or approved removable drive for backups

The application is designed for a single store workstation and binds its backend and database to
the local computer by default.

## Installation package checklist

Before visiting the customer, prepare:

- the signed `Simplified-Billing-<version>-x64.exe` installer;
- approved Java 21 and MySQL 8.4 installers when they are not already installed;
- the `database` support scripts from this release;
- a unique MySQL application password;
- a newly generated JWT secret;
- the store's legal name, address, phone, GST status, GSTIN, and logo;
- printer drivers and the correct receipt or label paper sizes;
- a secure method of handing owner and backup credentials to the customer.

Verify the Windows installer signature before use:

```powershell
Get-AuthenticodeSignature "C:\path\Simplified-Billing-0.1.0-x64.exe"
```

For a production release, `Status` should be `Valid` and the signer should be the expected
publisher.

## Part 1: Install workstation prerequisites

### Install Java 21

Install the organization-approved 64-bit Java 21 JDK or runtime. Ensure its `bin` directory is on
the Windows machine `Path`, then open a new PowerShell window and run:

```powershell
java -version
```

The output must identify Java 21. If the application package includes
`resources/runtime/bin/java.exe`, this separate step is not required for that release.

### Install MySQL 8.4

Install MySQL 8.4 as a Windows service with these settings:

- service startup: Automatic;
- TCP/IP enabled on `127.0.0.1`;
- port: `3306` unless the deployment has an approved alternative;
- strong administrator password;
- character set: `utf8mb4`;
- collation: `utf8mb4_0900_ai_ci`.

Verify the service and port:

```powershell
Get-Service *mysql*
Test-NetConnection 127.0.0.1 -Port 3306
```

The MySQL service should be `Running`, and `TcpTestSucceeded` should be `True`.

## Part 2: Prepare the new customer database

Locate the MySQL command-line client:

```powershell
$mysqlClient = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
& $mysqlClient --version
```

Run the new-store bootstrap script as the MySQL administrator:

```powershell
& $mysqlClient -u root -p --execute="SOURCE C:/path/to/database/01-new-store-bootstrap.sql"
```

Connect as the administrator:

```powershell
& $mysqlClient -u root -p
```

Create the restricted application account. Replace the example with a unique random password:

```sql
CREATE USER 'billing_app'@'127.0.0.1'
    IDENTIFIED BY 'replace-with-a-unique-random-password';
GRANT ALL PRIVILEGES ON billing.* TO 'billing_app'@'127.0.0.1';
FLUSH PRIVILEGES;
EXIT;
```

Do not use the MySQL root account for normal application operation.

Test the new account:

```powershell
& $mysqlClient -h 127.0.0.1 -u billing_app -p -e "SELECT CURRENT_USER(), DATABASE();" billing
```

## Part 3: Configure workstation secrets and paths

Generate a unique JWT signing secret in PowerShell:

```powershell
$jwtBytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Fill($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
```

Record the generated value in the customer's approved password vault. Never reuse the example JWT
secret from `.env.example`.

Create the following machine or user environment variables through **System Properties > Advanced
> Environment Variables**. Machine variables require administrator access and apply to every store
user; user variables apply only to the Windows account that runs billing.

| Variable | Required value |
|---|---|
| `BILLING_DB_URL` | `jdbc:mysql://127.0.0.1:3306/billing?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_ai_ci&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true` |
| `BILLING_DB_USERNAME` | `billing_app` |
| `BILLING_DB_PASSWORD` | The unique application-account password |
| `BILLING_JWT_SECRET_BASE64` | The generated Base64 JWT secret |
| `BILLING_MYSQL_CLIENT` | Full path to `mysql.exe` |
| `BILLING_MYSQLDUMP` | Full path to `mysqldump.exe` |

Optional variables are normally left at their defaults:

| Variable | Default |
|---|---|
| `BILLING_SERVER_ADDRESS` | `127.0.0.1` |
| `BILLING_SERVER_PORT` | `8080` |
| `BILLING_JWT_ISSUER` | `simplified-billing-desktop` |
| `BILLING_ACCESS_TOKEN_TTL` | `15m` |
| `BILLING_REFRESH_TOKEN_TTL` | `7d` |

Do not put secrets in shortcuts, batch files, email, the application installation directory, or
source control. Sign out of Windows or restart after changing machine-level variables so the Start
menu application receives them.

## Part 4: Install Simplified Billing

1. Right-click the signed installer and choose **Run as administrator** when required by company
   policy.
2. Review the publisher and installer version.
3. Choose the installation directory.
4. Leave the desktop and Start menu shortcuts enabled.
5. Finish the installation.
6. Start **Simplified Billing** from the Start menu.

On first start, the desktop application launches the packaged backend. Flyway automatically creates
all application tables and applies migrations V1 through V16. The database must already exist, but
its application tables should not be created manually.

The first startup can take longer than later startups. Do not close the application while database
migrations are running.

## Part 5: Complete first-run store setup

Enter and verify:

1. **Shop identity**: legal shop name and owner name.
2. **Address**: address lines, city, state, two-digit GST state code, and postal code.
3. **Contact**: phone and optional email.
4. **GST registration**:
   - enable GST only when the shop has a valid GSTIN;
   - enter the GSTIN exactly as registered;
   - when GST is disabled, POS uses simple billing and does not calculate CGST, SGST, or IGST.
5. **Invoice numbering**: invoice prefix and financial-year start month.
6. **Receipt defaults**: A4 or thermal and, for thermal, 58 mm or 80 mm width.
7. **Owner account**: unique username and a strong password known only to the owner.

After saving, sign in with the owner account. Store owner passwords cannot be recovered from the
database in plain text.

## Part 6: Configure branding and invoice printing

Open **Shop Settings** and configure:

- shop logo;
- default invoice output: A4 or thermal;
- A4 template;
- thermal template;
- receipt width;
- GST details, only if the business is GST registered.

The uploaded shop logo appears in the application and on supported invoice templates. It does not
change the installed Windows program icon.

### A4 printer

1. Install the manufacturer's Windows printer driver.
2. Print a Windows test page.
3. Set paper size to A4 and normal portrait orientation.
4. In POS or the invoice archive, use **Print A4**.
5. Confirm colors, margins, logo, totals, and that no blank pages are added.

### Thermal receipt printer

1. Install the manufacturer's driver.
2. Set the driver paper width to match 58 mm or 80 mm.
3. Turn off driver-added headers and footers when the driver exposes those options.
4. Choose the same width in Shop Settings.
5. In POS or the invoice archive, use **Print thermal**.
6. Confirm the receipt is not clipped and feeds only the required paper.

The Shop Settings selection is the default. POS still provides separate A4 and thermal buttons for
each completed bill.

### Barcode-label printer

1. Install and test the printer in Windows.
2. Load labels whose physical dimensions match the selected application label size.
3. Open a product and select **Print barcode labels**.
4. Print one label first.
5. Confirm there are no extra blank labels/pages, then print the required quantity.

## Part 7: Initial business configuration

Before live billing:

1. Add required users and assign only the roles they need.
2. Create categories.
3. Import or enter products, barcodes, units, prices, tax rates, and opening stock.
4. Add existing Khata customers and opening balances using the approved business process.
5. Add suppliers and opening payables where required.
6. Complete a controlled test sale and confirm stock deduction.
7. Reprint the test invoice in A4 and thermal formats.
8. Process a test sales return and find its `SR-...` document in Reports & Invoices.
9. Run a sales report and confirm net sales and sales returns are shown separately.

Do not reuse test invoice numbers or delete financial history. If the customer requires a clean
production database after acceptance testing, create it through the approved reset/new-store
procedure before entering real transactions.

## Part 8: Configure backup and recovery

Open the backup/system area and create an encrypted backup before the store starts live operation.

- Use a strong backup password and store it in the customer's password vault.
- The backup password is required for restore and cannot be recovered from the backup file.
- Save scheduled backups to a different physical drive or approved network folder.
- Do not rely only on the workstation's internal disk.
- Retain multiple generations according to the customer's policy.
- Test a restore on a non-production workstation before declaring backup setup complete.

The application creates `.sbk` encrypted backup files. Restore creates a pre-restore safety backup
before replacing the database. At startup, the application can offer recovery from the latest valid
local backup when one is available.

## Part 9: Final acceptance checklist

- [ ] Java 21 is available or a private runtime is bundled.
- [ ] MySQL starts automatically and listens only on the approved local interface.
- [ ] The `billing_app` account can access only the `billing` database.
- [ ] Customer-specific database and JWT secrets are configured.
- [ ] The application reaches login after a Windows restart.
- [ ] Store identity, GST state, GSTIN, and logo are correct.
- [ ] A non-GST shop produces no CGST, SGST, or IGST calculations.
- [ ] Product scan/search, stock deduction, checkout, and customer capture work.
- [ ] A4, thermal, and barcode-label test prints are correct.
- [ ] Saved invoices and `SR-...` sales-return invoices can be found and reprinted.
- [ ] Dashboard/report access is correct for Owner and Administrator roles.
- [ ] Encrypted backup, scheduled destination, password custody, and test restore are verified.
- [ ] The owner has received credentials and basic operating training securely.

## Upgrading an existing customer

1. Create and externally copy an encrypted backup.
2. Close Simplified Billing.
3. Run `database/02-upgrade-preflight.sql` and retain the output.
4. Verify the newer installer signature.
5. Install the newer version over the existing application.
6. Start it once and allow Flyway to apply only new migrations.
7. Run `database/03-upgrade-postcheck.sql`.
8. Compare invoice, return, product, customer, and purchase counts.
9. Test login, one invoice lookup, one return lookup, printing, reports, and backup.

Do not uninstall MySQL, delete the `billing` database, edit old migration files, or manually replay
V1–V16 during an application upgrade.

Uninstalling Simplified Billing intentionally keeps desktop application data, and it does not delete
the MySQL database. Remove customer data only through an explicitly approved data-retention process.

## Troubleshooting

### The application reports that the local service is unavailable

Check Java, the backend health endpoint, and port 8080:

```powershell
java -version
Invoke-RestMethod http://127.0.0.1:8080/api/v1/system/health
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
```

If another application owns port 8080, stop that application or use an approved alternate
`BILLING_SERVER_PORT` consistently.

### Database connection fails

```powershell
Get-Service *mysql*
Test-NetConnection 127.0.0.1 -Port 3306
```

Then verify `BILLING_DB_URL`, username, password, database name, and the `billing_app` grants.

### Backup or restore says a MySQL tool is missing

Confirm the full paths in `BILLING_MYSQL_CLIENT` and `BILLING_MYSQLDUMP` exist:

```powershell
Test-Path $env:BILLING_MYSQL_CLIENT
Test-Path $env:BILLING_MYSQLDUMP
```

### Printing is cancelled

Confirm the printer is online, has paper, is not paused, and is available in Windows. Retry with a
Windows test page, then choose the required A4 or thermal button again. A user closing the operating
system print dialog is also reported as a cancelled job.

### An upgrade reports a Flyway error

Stop using the application and preserve the log, installer version, preflight output, and backup.
Do not edit `flyway_schema_history`. Follow `Database-Migration-Guide.md` and escalate to support.

## Information to provide to support

- application version;
- Windows version;
- exact date/time and action that failed;
- invoice or return number when relevant;
- printer make/model and selected paper size;
- sanitized diagnostics export;
- backend log covering the failure;
- Flyway preflight/postcheck output for upgrade problems.

Never send database passwords, JWT secrets, owner passwords, backup passwords, or unencrypted
customer database dumps over email or chat.
