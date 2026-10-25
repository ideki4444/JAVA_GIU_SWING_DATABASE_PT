param(
    [ValidatePattern('^[a-zA-Z][a-zA-Z0-9_]*$')][string]$DatabaseName = 'aquatrack',
    [string]$MySqlBin = 'C:\xampp\mysql\bin',
    [string]$DatabaseUser = 'root'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$mysql = Join-Path $MySqlBin 'mysql.exe'
$dump = Join-Path $MySqlBin 'mysqldump.exe'
$connectionArgs = @('--host=127.0.0.1', "--user=$DatabaseUser", '--default-character-set=utf8mb4')
function Read-Query([string]$Sql) {
    $result = @(& $mysql @connectionArgs "--database=$DatabaseName" --batch --skip-column-names "--execute=$Sql")
    if ($LASTEXITCODE -ne 0) { throw 'Database query failed; migration verification did not complete.' }
    return ($result -join "`n")
}
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'dist\Aquatrack3-gpt-claude.jar'))) {
    throw 'Build the compatible application before applying this migration.'
}
$snapshots = [ordered]@{
    products = 'SELECT product_id,product_name,category,unit_price,stock_qty,reorder_level,is_active FROM products ORDER BY product_id'
    orders = 'SELECT * FROM orders ORDER BY order_id'
    order_items = 'SELECT * FROM order_items ORDER BY item_id'
    payments = 'SELECT * FROM payments ORDER BY payment_id'
    container_ledger = 'SELECT * FROM container_ledger ORDER BY ledger_id'
}
$before = @{}
foreach ($table in $snapshots.Keys) { $before[$table] = Read-Query $snapshots[$table] }
$hasDeliveryPrice = Read-Query "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='products' AND COLUMN_NAME='delivery_price'"
$configuredPrices = if ($hasDeliveryPrice -eq '1') {
    Read-Query 'SELECT product_id,delivery_price FROM products WHERE delivery_price IS NOT NULL ORDER BY product_id'
} else { '' }
$backupFolder = Join-Path $projectRoot 'backups'
New-Item -ItemType Directory -Force -Path $backupFolder | Out-Null
$backupPath = Join-Path $backupFolder ("{0}-before-delivery-prices-{1}.sql" -f $DatabaseName, (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
if (Test-Path -LiteralPath $backupPath) { throw 'Backup path already exists; no migration was applied.' }
& $dump @connectionArgs --single-transaction --routines --triggers "--result-file=$backupPath" $DatabaseName
if ($LASTEXITCODE -ne 0 -or (Get-Item -LiteralPath $backupPath).Length -eq 0) {
    throw 'Backup did not complete. No migration was applied.'
}
$migration = (Join-Path $projectRoot 'sql\001_delivery_prices.sql').Replace('\', '/')
& $mysql @connectionArgs "--database=$DatabaseName" "--execute=SOURCE $migration"
if ($LASTEXITCODE -ne 0) { throw "Migration failed. Preserve the backup at $backupPath and inspect the database before continuing." }
foreach ($table in $snapshots.Keys) {
    if ((Read-Query $snapshots[$table]) -cne $before[$table]) {
        throw "Existing $table rows changed during verification (possibly another running client). Backup: $backupPath. No automatic restore was attempted."
    }
}
if ($configuredPrices) {
    $configuredIds = @($configuredPrices -split "`n" | ForEach-Object { ($_ -split "`t")[0] }) -join ','
    if ((Read-Query "SELECT product_id,delivery_price FROM products WHERE product_id IN ($configuredIds) ORDER BY product_id") -cne $configuredPrices) {
        throw 'An existing delivery price changed unexpectedly; review the backup before continuing.'
    }
}
if ((Read-Query "SELECT COUNT(*) FROM products WHERE category='Refill' AND delivery_price IS NULL") -ne '0') {
    throw 'Some refills still lack a delivery price.'
}
if ((Read-Query "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='products' AND CONSTRAINT_NAME='chk_products_delivery_price'") -ne '1') {
    throw 'The delivery-price constraint was not installed.'
}
Write-Output "Migration verified on $DatabaseName. Existing product fields, sales, order items, payments and container movements are unchanged."
Write-Output "Backup: $backupPath"
Write-Output 'Initial delivery prices match pickup prices. Owners can set their station delivery rates in Products & Prices.'
