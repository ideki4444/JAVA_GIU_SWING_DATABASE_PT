param([switch]$Test)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $projectRoot
try {
    $compiler = (Get-Command javac -ErrorAction Stop).Source
    $java = Join-Path (Split-Path $compiler) 'java.exe'
    $jar = Join-Path (Split-Path $compiler) 'jar.exe'
    $compiled = Join-Path $projectRoot 'build\package-classes'
    # Remove only this build script's generated classes, after checking its absolute location.
    if (-not $compiled.StartsWith((Join-Path $projectRoot 'build') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Build output must stay within this project build directory.'
    }
    if (Test-Path -LiteralPath $compiled) { Remove-Item -LiteralPath $compiled -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $compiled,'dist\lib' | Out-Null
    $sources = @(Get-ChildItem -LiteralPath src -Recurse -Filter '*.java' | ForEach-Object FullName)
    & $compiler --release 25 -encoding UTF-8 -cp 'lib\mysql-connector-j-26.7.0.jar' -d $compiled $sources
    if ($LASTEXITCODE -ne 0) { throw 'Application compilation failed.' }
    if ($Test) {
        $testClasses = Join-Path $projectRoot 'build\test-classes'
        New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
        $testSources = @(Get-ChildItem -LiteralPath test -Recurse -Filter '*.java' | ForEach-Object FullName)
        & $compiler --release 25 -encoding UTF-8 -cp "$compiled;lib\mysql-connector-j-26.7.0.jar" -d $testClasses $testSources
        if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
        $tests = @(
            'aquatrack.ui.FixedSplitLayoutTest', 'aquatrack.ui.DashboardLayoutTest', 'aquatrack.ui.LoginAndThemeTest', 'aquatrack.ui.WorkspaceViewportTest',
            'aquatrack.ui.ProductInventoryFlowTest', 'aquatrack.ui.OrderPricingFlowTest',
            'aquatrack.dao.OrderTransactionTest', 'aquatrack.dao.LedgerAccountsTransactionTest',
            'aquatrack.ui.LedgerAccountsUiTest', 'aquatrack.util.PrintDocumentTest',
            'aquatrack.ui.PrintPreviewFormatTest', 'aquatrack.ui.ReceiptPreviewTest', 'aquatrack.ui.ReportPrintSnapshotTest'
        )
        foreach ($testClass in $tests) {
            & $java '-Djava.awt.headless=true' -cp "$compiled;$testClasses;lib\mysql-connector-j-26.7.0.jar" $testClass
            if ($LASTEXITCODE -ne 0) { throw "$testClass failed." }
        }
    }
    $manifestPath = Join-Path $projectRoot 'build\package-manifest.mf'
    [IO.File]::WriteAllText($manifestPath, "Manifest-Version: 1.0`nMain-Class: aquatrack.Main`nClass-Path: lib/mysql-connector-j-26.7.0.jar`n`n", [Text.UTF8Encoding]::new($false))
    & $jar --create --file 'dist\Aquatrack3-gpt-claude.jar' --manifest $manifestPath -C $compiled .
    if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
    Copy-Item -LiteralPath 'lib\mysql-connector-j-26.7.0.jar' -Destination 'dist\lib\mysql-connector-j-26.7.0.jar' -Force
    Write-Output 'Build complete: dist\Aquatrack3-gpt-claude.jar'
} finally { Pop-Location }
