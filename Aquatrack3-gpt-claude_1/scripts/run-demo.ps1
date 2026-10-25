param(
    [ValidatePattern('^aquatrack_demo_[a-z0-9_]{1,40}$')]
    [string]$DatabaseName = 'aquatrack_demo_usability_20261023',
    [string]$DatabaseUser = 'root'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$package = Join-Path $projectRoot 'dist\Aquatrack3-gpt-claude.jar'
if (-not (Test-Path -LiteralPath $package)) { throw 'Build AquaTrack before starting the demo.' }
$url = "jdbc:mysql://127.0.0.1:3306/${DatabaseName}?serverTimezone=Asia/Manila&connectTimeout=5000&socketTimeout=15000"
& java "-Daquatrack.db.url=$url" "-Daquatrack.db.user=$DatabaseUser" -jar $package
if ($LASTEXITCODE -ne 0) { throw 'The demo application did not exit normally.' }
