# Building the Simplified Billing Windows EXE

This is the release guide for a new developer who needs to convert this repository into a Windows
installer and hand it to a customer or installation technician. Follow the steps in order for every
release; do not package directly from an unverified development build.

Companion documents:

- `Database-Migration-Guide.md` explains clean-store and existing-store migration handling.
- `Customer-Setup-Guide.md` explains workstation, database, printer, first-run, and backup setup.
- `database/01-new-store-bootstrap.sql` prepares an empty customer database.
- `database/02-upgrade-preflight.sql` and `03-upgrade-postcheck.sql` verify upgrades.

The generated installer contains:

- the Electron desktop application;
- the compiled React user interface;
- the Spring Boot backend JAR.

The current installer does **not** include MySQL or a private Java runtime. A computer that runs
the current installer must therefore have Java 21 and MySQL installed and configured. Review the
[optional Java runtime bundle](#12-optional-java-runtime-bundle) and
[MySQL distribution warning](#13-mysql-distribution-warning) before distributing it to customers.

## Release output and build flow

The release pipeline is:

```text
Flyway SQL -> Spring Boot JAR -> React production assets -> Electron package -> NSIS .exe
```

Electron Builder copies `backend/target/billing-backend.jar` into the installer's
`resources/backend` directory. The Flyway SQL files are already inside that JAR, so the installed
backend can initialize a blank `billing` database or upgrade an older store automatically.

The internal release folder supplied to support should contain the signed `.exe`, the three
`database/*.sql` operational scripts, `Customer-Setup-Guide.md`, the release notes, and a checksum.
Do not include source code, `.env`, database passwords, JWT secrets, signing keys, or customer data.

## 1. Build-machine requirements

Use a 64-bit Windows computer with internet access for the first build. Install:

1. Git.
2. JDK 21, including `java`, `javac`, `jlink`, and `jar`.
3. Maven 3.9 or newer.
4. Node.js 24 LTS.
5. npm.
6. MySQL 8.4 when running database or migration tests.

Open PowerShell and confirm the tools:

```powershell
java -version
javac -version
mvn -version
node --version
npm --version
```

`java` and Maven must report Java 21. Node and npm must be available from the same PowerShell
window used for the build.

## 2. Open the repository

Open PowerShell at the repository root:

```powershell
Set-Location "C:\path\to\VibeCoding"
```

The following paths must exist:

```powershell
Test-Path .\backend\pom.xml
Test-Path .\desktop\package.json
Test-Path .\desktop\electron-builder.yml
```

All three commands should print `True`.

## 3. Set the application version

The installer version comes from `desktop/package.json`. Update it before a release, for example:

```json
{
  "version": "1.0.0"
}
```

Use a numeric semantic version such as `1.0.0`, `1.0.1`, or `1.1.0`. Commit the version change so
the source code and installer can be traced to the same release.

## 4. Install dependencies

Maven downloads backend dependencies during the backend build. Install the exact desktop
dependencies recorded in `package-lock.json`:

```powershell
npm --prefix desktop ci
```

Do not use `npm install` for a normal release build because it can update the dependency lock.

On a corporate Windows network, if Node does not recognize the company certificate authority:

```powershell
$env:NODE_USE_SYSTEM_CA = "1"
npm --prefix desktop ci
```

Never use `NODE_TLS_REJECT_UNAUTHORIZED=0`.

## 5. Verify the backend

Run the backend tests and release checks:

```powershell
mvn -f backend/pom.xml clean verify
```

This command runs the test suite and the JaCoCo coverage gate. The first execution requires
internet access to populate the Maven cache.

For a quick compilation check without the release coverage gate:

```powershell
mvn -f backend/pom.xml compiler:compile
mvn -f backend/pom.xml compiler:testCompile
```

The full `clean verify` command is recommended for every installer that will be distributed.

## 6. Build the backend JAR

Create the executable Spring Boot JAR:

```powershell
mvn -f backend/pom.xml clean package
```

Confirm the output:

```powershell
Test-Path .\backend\target\billing-backend.jar
Get-Item .\backend\target\billing-backend.jar | Select-Object FullName,Length,LastWriteTime
```

The first command must print `True`. Electron Builder expects exactly this filename and location.

### Verify the database migrations inside the JAR

Confirm that every Flyway migration was packaged:

```powershell
$migrationEntries = jar tf .\backend\target\billing-backend.jar |
    Select-String "BOOT-INF/classes/db/migration/V[0-9]+__.*\.sql"
$migrationEntries
"Packaged migration count: $($migrationEntries.Count)"
```

For the current release, the list must contain V1 through V16. Flyway sorts versions numerically;
the alphabetical file order shown by Windows Explorer is not the execution order.

Do not build a combined SQL schema file for upgrades. Existing stores must be upgraded by Flyway so
checksums, installed versions, and failed states remain auditable. Follow
`Database-Migration-Guide.md` for clean-store and upgrade testing.

## 7. Verify the desktop application

Run the TypeScript check and build the production React renderer:

```powershell
npm --prefix desktop run typecheck
npm --prefix desktop run build
```

Confirm that `desktop/dist` was generated:

```powershell
Test-Path .\desktop\dist\index.html
```

## 8. Build the Windows installer

From the repository root, run:

```powershell
npm --prefix desktop run package:win
```

The `package:win` script performs the desktop build again and then runs Electron Builder with the
64-bit NSIS target configured in `desktop/electron-builder.yml`.

The expected installer is:

```text
desktop/release/Simplified-Billing-1.0.0-x64.exe
```

The version in the filename matches `desktop/package.json`. List the generated files with:

```powershell
Get-ChildItem .\desktop\release
```

Do not distribute the `win-unpacked` directory as the installer. Distribute the generated `.exe`
after completing the tests and signing steps below.

## 9. Test the installer

Test on a clean Windows virtual machine or a separate test computer, not only on the build machine.

The current package requires the test computer to have:

- Java 21 available as `java` on `PATH`;
- MySQL running locally on `127.0.0.1:3306`;
- a `billing` database and a restricted application account;
- the database connection and JWT environment variables described below;
- `mysql` and `mysqldump` on `PATH` when backup and restore will be used.

Run the installer:

```powershell
& ".\desktop\release\Simplified-Billing-1.0.0-x64.exe"
```

During installation:

1. Choose the installation directory.
2. Allow the Start menu and desktop shortcuts to be created.
3. Start Simplified Billing.
4. Confirm that the first-run setup screen opens.
5. Create a test shop and owner account.
6. Upload a shop logo and confirm it appears in the sidebar and on a printed bill.
7. Create a product, complete a sale, print a receipt, and process a return.
8. Create an encrypted backup, close the application, and test restoration.
9. Restart Windows and confirm MySQL and the application can start again.

For a clean-store acceptance test, run `database/01-new-store-bootstrap.sql`, start the installed
application, and then run `database/03-upgrade-postcheck.sql`. For an upgrade acceptance test, take
a copy of a database from the previous release, run the preflight script, start the new installer,
and compare the postcheck results. Never use a customer's only production database for release
testing.

Uninstalling the desktop application deliberately keeps its application-data directory. This
protects local recovery data during an application reinstall.

## 10. Target-computer database configuration

Create the database and restricted account with a MySQL administrator:

```sql
CREATE DATABASE billing
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'billing_app'@'127.0.0.1'
    IDENTIFIED BY 'replace-with-a-long-random-password';

GRANT ALL PRIVILEGES ON billing.* TO 'billing_app'@'127.0.0.1';
FLUSH PRIVILEGES;
```

Generate a separate JWT secret for each installation:

```powershell
$jwtBytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Fill($jwtBytes)
$jwtSecret = [Convert]::ToBase64String($jwtBytes)
```

The installed application reads these variables when it starts its packaged backend:

```powershell
$env:BILLING_DB_URL = "jdbc:mysql://127.0.0.1:3306/billing?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_ai_ci&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
$env:BILLING_DB_USERNAME = "billing_app"
$env:BILLING_DB_PASSWORD = "replace-with-the-database-password"
$env:BILLING_JWT_SECRET_BASE64 = $jwtSecret
$env:BILLING_MYSQL_CLIENT = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
$env:BILLING_MYSQLDUMP = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe"
```

Variables set only with `$env:` last for the current PowerShell process. For an installed release,
provide them through an approved Windows service/launcher or secure machine configuration. Do not
put real passwords or JWT secrets in Git, `package.json`, Electron source files, or the installer.

## 11. Optional Windows EXE icon

The shop logo uploaded inside the application is store data. It appears in the application sidebar
and on receipts, but it does not change the Windows `.exe` icon.

For a fixed Windows program icon, create a multi-resolution ICO file containing at least 16, 32,
48, 128, and 256 pixel images. Store it as:

```text
desktop/build/icon.ico
```

Then add the icon to the `win` section in `desktop/electron-builder.yml`:

```yaml
win:
  icon: build/icon.ico
  target:
    - target: nsis
      arch:
        - x64
```

You can also add `installerIcon: build/icon.ico` under the `nsis` section. Rebuild the installer
after changing the icon.

## 12. Optional Java runtime bundle

The Electron main process first looks for `resources/runtime/bin/java.exe`, then falls back to the
computer's `PATH`. To avoid requiring a separate Java installation, build a private Java 21 runtime:

```powershell
jlink `
  --add-modules java.se,jdk.crypto.ec,jdk.unsupported `
  --strip-debug `
  --no-header-files `
  --no-man-pages `
  --compress=2 `
  --output .\desktop\resources\runtime
```

Add it to `extraResources` in `desktop/electron-builder.yml`:

```yaml
extraResources:
  - from: ../backend/target/billing-backend.jar
    to: backend/billing-backend.jar
  - from: resources/runtime
    to: runtime
    filter:
      - "**/*"
```

Then rerun:

```powershell
npm --prefix desktop run package:win
```

Test this runtime on a clean machine. Review the JDK vendor's redistribution terms before shipping
it. The broad `java.se` module set prioritizes compatibility over minimum installer size.

## 13. MySQL distribution warning

The current project connects to MySQL but does not bundle or install MySQL. A genuinely standalone
installer requires an approved database distribution, initialization, Windows service lifecycle,
upgrade, backup, and uninstall strategy.

MySQL Community Edition is GPL licensed. Obtain project-specific legal/licensing approval before
redistributing MySQL binaries as part of a proprietary installer. Until that work is completed,
install and configure MySQL separately on each target computer.

## 14. Code signing

Unsigned installers can trigger Windows SmartScreen warnings. Production installers should be
signed with an organization-owned code-signing certificate.

Electron Builder supports certificate variables such as:

```powershell
$env:CSC_LINK = "C:\secure\codesigning-certificate.pfx"
$env:CSC_KEY_PASSWORD = "certificate-password"
npm --prefix desktop run package:win
```

Keep the certificate and password outside the repository. Prefer an EV certificate or managed
signing service when required by the organization's release policy. Verify the final signature:

```powershell
Get-AuthenticodeSignature .\desktop\release\Simplified-Billing-1.0.0-x64.exe
```

The status should be `Valid` for a signed production installer.

## 15. Release checklist

Before distributing the `.exe`, confirm:

- [ ] The version was updated in `desktop/package.json`.
- [ ] `mvn clean verify` passed.
- [ ] The real MySQL migration test passed on a disposable database.
- [ ] `npm ci`, desktop type checking, and the production build passed.
- [ ] `backend/target/billing-backend.jar` was created before packaging.
- [ ] The JAR contains every released Flyway SQL file from V1 through the current version.
- [ ] Both blank-to-latest and previous-to-latest migrations passed on disposable MySQL 8.4 databases.
- [ ] The installer was tested on a clean 64-bit Windows machine.
- [ ] Java and MySQL prerequisites, or approved bundled runtimes, were verified.
- [ ] Billing, receipt printing, uploaded logo printing, returns, backup, and restore were tested.
- [ ] Secrets are not embedded in source files or the installer.
- [ ] The installer is signed and its signature is valid.
- [ ] An encrypted backup was created before upgrading an existing installation.
- [ ] The signed installer, database support SQL, customer guide, release notes, and checksum are in the handoff package.

## Troubleshooting

### `backend/target/billing-backend.jar` is missing

Run:

```powershell
mvn -f backend/pom.xml clean package
```

Do not run Electron Builder until the JAR exists.

### Maven cannot download JaCoCo or Surefire

The local Maven cache is incomplete or the network blocks Maven Central. Connect to an approved
network and run:

```powershell
mvn -U -f backend/pom.xml clean verify
```

Do not treat `compiler:compile` alone as release verification.

### npm or Electron download fails

Verify proxy/firewall access to the npm registry and Electron release downloads. On a corporate
certificate network, set `NODE_USE_SYSTEM_CA=1` and retry `npm ci`.

### The installed application says Java is missing

Install Java 21 and add its `bin` directory to `PATH`, or bundle the private runtime described
above. Restart Windows after changing machine-wide environment variables.

### The installed application cannot connect to MySQL

Confirm:

```powershell
Get-Service *mysql*
Test-NetConnection 127.0.0.1 -Port 3306
```

Then verify the database URL, username, password, database name, and account permissions.

### Windows displays an unknown publisher warning

The installer is unsigned or the signature is not trusted. Sign the production installer with an
approved code-signing certificate and verify it with `Get-AuthenticodeSignature`.
