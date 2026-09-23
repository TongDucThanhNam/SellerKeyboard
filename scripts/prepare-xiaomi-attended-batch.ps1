[CmdletBinding()]
param(
    [string]$OutputDirectory = ""
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repositoryRoot "gradlew.bat"
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repositoryRoot `
        "docs\evidence\verification\xiaomi-attended\prepared"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$tasks = @(
    ':app:testDebugUnitTest',
    ':app:candidateCorpusTest',
    ':app:sellerWorkflowBenchmark',
    ':app:keyDispatchPerformanceTest',
    ':app:lintDebug',
    ':app:assembleUpgradeFixture',
    ':app:assembleDebug',
    ':app:assembleDebugAndroidTest',
    ':app:assembleBenchmark',
    ':app:assembleRelease',
    ':benchmark-host:assembleBenchmark',
    ':benchmark:assembleBenchmark'
)
& $gradle @tasks --console=plain
if ($LASTEXITCODE -ne 0) {
    throw "Xiaomi batch preparation build failed."
}

$artifacts = @(
    [ordered]@{
        approval = 1
        name = 'SellerKeyboard v24 upgrade fixture target'
        package = 'com.terasumi.sellerkeyboard'
        path = 'app\build\outputs\apk\upgradeFixture\app-upgradeFixture.apk'
    },
    [ordered]@{
        approval = 2
        name = 'SellerKeyboard connected tests'
        package = 'com.terasumi.sellerkeyboard.test'
        path = 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    },
    [ordered]@{
        approval = 3
        name = 'Neutral benchmark editor host'
        package = 'com.terasumi.sellerkeyboard.benchmarkhost'
        path = 'benchmark-host\build\outputs\apk\benchmark\benchmark-host-benchmark.apk'
    },
    [ordered]@{
        approval = 4
        name = 'SellerKeyboard optimized benchmark target'
        package = 'com.terasumi.sellerkeyboard'
        path = 'app\build\outputs\apk\benchmark\app-benchmark.apk'
    },
    [ordered]@{
        approval = 5
        name = 'Macrobenchmark runner'
        package = 'com.terasumi.sellerkeyboard.benchmark'
        path = 'benchmark\build\outputs\apk\benchmark\benchmark-benchmark.apk'
    }
)

$manifestArtifacts = foreach ($artifact in $artifacts) {
    $absolutePath = Join-Path $repositoryRoot $artifact.path
    if (-not (Test-Path -LiteralPath $absolutePath)) {
        throw "Prepared APK is missing: $absolutePath"
    }
    $file = Get-Item -LiteralPath $absolutePath
    [ordered]@{
        approval = $artifact.approval
        name = $artifact.name
        package = $artifact.package
        path = $artifact.path
        bytes = $file.Length
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $absolutePath).Hash
    }
}

$apkanalyzer = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME 'cmdline-tools\latest\bin\apkanalyzer.bat'
} else {
    (Get-Command apkanalyzer.bat -ErrorAction SilentlyContinue).Source
}
if ([string]::IsNullOrWhiteSpace($apkanalyzer) -or
    -not (Test-Path -LiteralPath $apkanalyzer)) {
    throw 'apkanalyzer is required to audit the prepared benchmark/release boundary.'
}
$benchmarkApk = Join-Path $repositoryRoot `
    'app\build\outputs\apk\benchmark\app-benchmark.apk'
$upgradeFixtureApk = Join-Path $repositoryRoot `
    'app\build\outputs\apk\upgradeFixture\app-upgradeFixture.apk'
$releaseApk = Get-ChildItem -Path `
    (Join-Path $repositoryRoot 'app\build\outputs\apk\release') -Filter '*.apk' |
    Select-Object -First 1
if ($null -eq $releaseApk) { throw 'Release APK is missing after assembleRelease.' }
$benchmarkManifest = (& $apkanalyzer manifest print $benchmarkApk 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the benchmark APK manifest.' }
$releaseManifest = (& $apkanalyzer manifest print $releaseApk.FullName 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the release APK manifest.' }
$upgradeFixtureVersionCode = [int]((
    & $apkanalyzer manifest version-code $upgradeFixtureApk 2>&1
) -join '').Trim()
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the upgrade-fixture version code.' }
$benchmarkVersionCode = [int]((
    & $apkanalyzer manifest version-code $benchmarkApk 2>&1
) -join '').Trim()
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the benchmark version code.' }
$benchmarkHasBridge = $benchmarkManifest.Contains(
    'com.terasumi.sellerkeyboard.ime-test-bridge'
)
$benchmarkDebuggable = $benchmarkManifest -match 'android:debuggable="true"'
$releaseHasBridge = $releaseManifest.Contains('com.terasumi.sellerkeyboard.ime-test-bridge')
$releaseDebuggable = $releaseManifest -match 'android:debuggable="true"'
if (-not $benchmarkHasBridge -or $benchmarkDebuggable -or
    $releaseHasBridge -or $releaseDebuggable) {
    throw 'Prepared APK security boundary failed: bridge/debuggable manifest mismatch.'
}
if ($upgradeFixtureVersionCode -ge $benchmarkVersionCode) {
    throw "Prepared APK upgrade boundary failed: $upgradeFixtureVersionCode -> $benchmarkVersionCode"
}

[ordered]@{
    generatedAt = (Get-Date).ToString('o')
    repositoryRoot = $repositoryRoot
    approvalCount = 5
    artifacts = $manifestArtifacts
    securityBoundary = [ordered]@{
        benchmarkHasShellGuardedBridge = $benchmarkHasBridge
        benchmarkDebuggable = $benchmarkDebuggable
        releaseHasTestBridge = $releaseHasBridge
        releaseDebuggable = $releaseDebuggable
        releaseApk = $releaseApk.FullName
        upgradeFixtureVersionCode = $upgradeFixtureVersionCode
        benchmarkVersionCode = $benchmarkVersionCode
        versionCodeUpgradePrepared = $true
    }
    nextCommand = '.\scripts\run-xiaomi-attended-batch.ps1 -Serial <adb-serial>'
} | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 `
    (Join-Path $OutputDirectory 'manifest.json')

Write-Host "Xiaomi attended batch is prepared."
Write-Host "No device approval was needed during preparation."
Write-Host "When the phone owner is present, run:"
Write-Host "  .\scripts\run-xiaomi-attended-batch.ps1 -Serial <adb-serial>"
Write-Host "Approve five MIUI install prompts in one attended window."
Write-Host "Prompts 4/5 appear back-to-back after the connected suite; an audible cue is emitted."
Write-Host "Before prompt 4, a reversible data-preservation fixture is seeded automatically."
Write-Host "After prompt 5, upgrade preservation, voice permission/provider, physical emoji, and remaining evidence run automatically without another install."
