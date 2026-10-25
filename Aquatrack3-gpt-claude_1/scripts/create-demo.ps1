param(
    [ValidatePattern('^aquatrack_demo_[a-z0-9_]{1,40}$')]
    [string]$DatabaseName = ('aquatrack_demo_' + [DateTime]::UtcNow.ToString('yyyyMMdd_HHmmss') + '_' + [Guid]::NewGuid().ToString('N').Substring(0, 6)),
    [string]$MySqlPath = 'C:\xampp\mysql\bin\mysql.exe',
    [ValidatePattern('^[A-Za-z0-9_.-]{1,32}$')]
    [string]$DatabaseUser = 'root'
)

# Creates a fresh local participant database. Existing databases are never reused or removed.
# If MySQL needs a password, set AQUATRACK_DB_PASSWORD in this PowerShell session first.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$classes = Join-Path $projectRoot 'build\package-classes'
$connector = Join-Path $projectRoot 'lib\mysql-connector-j-26.7.0.jar'
$demoClasses = Join-Path $projectRoot 'build\demo-tools'
$baseline = Join-Path $projectRoot 'sql\schema-before-usability.sql'
$migration = Join-Path $projectRoot 'sql\001_delivery_prices.sql'

# Revalidate even the default value before forming a SQL identifier or a JDBC URL.
if ($DatabaseName -cnotmatch '^aquatrack_demo_[a-z0-9_]{1,40}$') {
    throw 'The database name must start with aquatrack_demo_ and contain only lowercase letters, numbers and underscores.'
}
if (-not (Test-Path -LiteralPath $MySqlPath -PathType Leaf)) { throw "MySQL client was not found at $MySqlPath." }
if (-not (Test-Path -LiteralPath (Join-Path $classes 'aquatrack\Main.class') -PathType Leaf)) {
    throw 'Build the application first: powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1'
}
foreach ($required in @($connector, $baseline, $migration)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required project file is missing: $required" }
}
$compiler = (Get-Command javac -ErrorAction Stop).Source
$java = Join-Path (Split-Path $compiler) 'java.exe'
New-Item -ItemType Directory -Force -Path $demoClasses | Out-Null
& $compiler --release 25 -encoding UTF-8 -cp "$classes;$connector" -d $demoClasses (Join-Path $PSScriptRoot 'DemoSeed.java')
if ($LASTEXITCODE -ne 0) { throw 'Demo helper compilation failed; no database was created.' }

$clientArgs = @('--host=127.0.0.1', '--port=3306', '--protocol=TCP', "--user=$DatabaseUser",
    '--default-character-set=utf8mb4', '--batch', '--skip-column-names', '--connect-timeout=5')
$previousMySqlPassword = $env:MYSQL_PWD
$previousOutputEncoding = $OutputEncoding
$created = $false
try {
    # The password is inherited by mysql, never placed in its command line or written to a file.
    $env:MYSQL_PWD = if ($null -eq $env:AQUATRACK_DB_PASSWORD) { '' } else { $env:AQUATRACK_DB_PASSWORD }
    $OutputEncoding = [Text.UTF8Encoding]::new($false)
    $existing = & $MySqlPath @clientArgs --execute="SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$DatabaseName'"
    if ($LASTEXITCODE -ne 0) { throw 'Could not check the local MySQL server.' }
    if ([int]($existing | Select-Object -Last 1) -ne 0) {
        throw "Refusing to reuse existing database $DatabaseName. Run again with a new aquatrack_demo_ name."
    }

    # No IF NOT EXISTS: a simultaneous creator must fail rather than reuse another database.
    & $MySqlPath @clientArgs --execute="CREATE DATABASE $DatabaseName CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    if ($LASTEXITCODE -ne 0) { throw 'Creating the new isolated demo database failed.' }
    $created = $true
    Get-Content -LiteralPath $baseline -Raw -Encoding UTF8 | & $MySqlPath @clientArgs "--database=$DatabaseName" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Loading the baseline schema failed.' }
    Get-Content -LiteralPath $migration -Raw -Encoding UTF8 | & $MySqlPath @clientArgs "--database=$DatabaseName" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Applying delivery-price migration failed.' }

    $url = "jdbc:mysql://127.0.0.1:3306/${DatabaseName}?serverTimezone=Asia/Manila&connectTimeout=5000&socketTimeout=15000"
    & $java "-Daquatrack.db.url=$url" "-Daquatrack.db.user=$DatabaseUser" -cp "$classes;$demoClasses;$connector" DemoSeed $DatabaseName
    if ($LASTEXITCODE -ne 0) { throw 'Seeding the isolated demo database failed.' }

    Write-Output "Demo database ready: $DatabaseName"
    Write-Output 'Launch this participant session with the command below. It always selects the isolated demo database:'
    # Single-quote PowerShell literals safely, including project paths containing spaces or apostrophes.
    $javaLiteral = "'" + $java.Replace("'", "''") + "'"
    $urlLiteral = "'" + ("-Daquatrack.db.url=$url").Replace("'", "''") + "'"
    $userLiteral = "'" + ("-Daquatrack.db.user=$DatabaseUser").Replace("'", "''") + "'"
    $classpathLiteral = "'" + ("$classes;$connector").Replace("'", "''") + "'"
    Write-Output "& $javaLiteral $urlLiteral $userLiteral -cp $classpathLiteral aquatrack.Main"
    Write-Output 'Create another new database for the next participant or comparison round; no reset or deletion is performed.'
} catch {
    if ($created) {
        Write-Warning "The new database $DatabaseName is retained for inspection. Use a different new name for a retry."
    }
    throw
} finally {
    $env:MYSQL_PWD = $previousMySqlPassword
    $OutputEncoding = $previousOutputEncoding
}
