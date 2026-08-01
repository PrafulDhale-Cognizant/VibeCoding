param(
    [string]$DatabaseUrl = $env:BILLING_DB_URL,
    [string]$DatabaseUser = $env:BILLING_DB_USERNAME,
    [string]$DatabasePassword = $env:BILLING_DB_PASSWORD,
    [string]$MySqlExecutable = "mysql"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($DatabaseUrl)) {
    $DatabaseUrl = "jdbc:mysql://127.0.0.1:3306/billing"
}
if ([string]::IsNullOrWhiteSpace($DatabaseUser)) {
    $DatabaseUser = "billing_app"
}
if ([string]::IsNullOrWhiteSpace($DatabasePassword)) {
    throw "Set BILLING_DB_PASSWORD in this terminal before running the repair."
}

$match = [regex]::Match($DatabaseUrl, '^jdbc:mysql://(?<host>[^:/?]+)(:(?<port>\d+))?/(?<database>[^?]+)')
if (-not $match.Success) {
    throw "BILLING_DB_URL must be a jdbc:mysql URL."
}

$databaseHost = $match.Groups['host'].Value
$databasePort = if ($match.Groups['port'].Success) { $match.Groups['port'].Value } else { "3306" }
$databaseName = $match.Groups['database'].Value
$env:MYSQL_PWD = $DatabasePassword

try {
    $failedCountSql = "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '9' AND success = 0;"
    $failedCount = & $MySqlExecutable --batch --skip-column-names `
        --host=$databaseHost --port=$databasePort --user=$DatabaseUser $databaseName `
        --execute=$failedCountSql
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect Flyway history."
    }
    if ([int]$failedCount -eq 0) {
        Write-Host "No failed Flyway V9 entry was found; nothing was changed."
        exit 0
    }

    $successfulCountSql = "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '9' AND success = 1;"
    $successfulCount = & $MySqlExecutable --batch --skip-column-names `
        --host=$databaseHost --port=$databasePort --user=$DatabaseUser $databaseName `
        --execute=$successfulCountSql
    if ($LASTEXITCODE -ne 0 -or [int]$successfulCount -ne 0) {
        throw "V9 has a successful history entry or history could not be validated; refusing repair."
    }

    $repairSql = "DELETE FROM flyway_schema_history WHERE version = '9' AND success = 0;"
    & $MySqlExecutable --host=$databaseHost --port=$databasePort --user=$DatabaseUser `
        $databaseName --execute=$repairSql
    if ($LASTEXITCODE -ne 0) {
        throw "Flyway V9 history repair failed."
    }
    Write-Host "Removed the failed V9 history entry. Restart the backend to apply the corrected migration."
}
finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
