[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$OutputDirectory = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
    [IO.Path]::GetFullPath($OutputDirectory)
} else {
    [IO.Path]::GetFullPath((Join-Path (Get-Location).Path $OutputDirectory))
}
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
} else { 'adb' }
$package = 'com.terasumi.sellerkeyboard'
$testPackage = 'com.terasumi.sellerkeyboard.test'
$runner = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
$testClass = 'com.terasumi.sellerkeyboard.voice.AndroidSpeechRecognizerRepositoryInstrumentedTest'
$appApk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $root 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$instrumentationLog = Join-Path $OutputDirectory 'instrumentation.txt'
$appAttributionRoot = Join-Path $OutputDirectory 'app-uid-attribution'
$providerAttributionRoot = Join-Path $OutputDirectory 'provider-uid-attribution'
$expectedTests = @(
    'a_explicitService_deliversPartialProcessingAndReviewableFinalResult',
    'b_explicitService_mapsNetworkAndNoMatchErrors',
    'z_cancel_preventsDelayedProviderResultFromRepopulatingState'
)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: $($Arguments -join ' ')`n$($result -join "`n")"
    }
    $result
}

if (-not $SkipBuild) {
    $buildLog = Join-Path $OutputDirectory 'build.txt'
    $buildOutput = @(
        & (Join-Path $root 'gradlew.bat') :app:assembleDebug `
            :app:assembleDebugAndroidTest --console=plain 2>&1
    )
    $buildOutput | Set-Content -Encoding utf8 -LiteralPath $buildLog
    if ($LASTEXITCODE -ne 0) {
        throw "Voice integration APK build failed. See $buildLog"
    }
}

if (-not (Test-Path -LiteralPath $appApk)) { throw "Missing APK: $appApk" }
if (-not (Test-Path -LiteralPath $testApk)) { throw "Missing APK: $testApk" }
Adb '-s' $Serial install '-r' '-t' $appApk | Out-Null
& $adb -s $Serial uninstall $testPackage 2>&1 | Out-Null
Adb '-s' $Serial install '-t' $testApk | Out-Null
Adb '-s' $Serial shell pm grant $testPackage android.permission.RECORD_AUDIO | Out-Null
Adb '-s' $Serial shell pm grant $package android.permission.RECORD_AUDIO | Out-Null
Adb '-s' $Serial shell am force-stop $package | Out-Null
Start-Sleep -Milliseconds 750

& (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
    -Phase Start -Serial $Serial -PackageName $package `
    -OutputDirectory $appAttributionRoot -NetworkExpectation Zero
& (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
    -Phase Start -Serial $Serial -PackageName $testPackage `
    -OutputDirectory $providerAttributionRoot -NetworkExpectation Zero

$instrumentation = @(
    Adb '-s' $Serial shell am instrument '-w' '-r' '-e' class $testClass $runner
)
$instrumentation | Set-Content -Encoding utf8 -LiteralPath $instrumentationLog
$instrumentationText = $instrumentation -join "`n"
if ($instrumentationText -notmatch 'OK \(3 tests\)' -or
    $instrumentationText -match 'FAILURES!!!') {
    throw "Deterministic voice integration matrix failed. See $instrumentationLog"
}
foreach ($test in $expectedTests) {
    if ($instrumentationText -notmatch [regex]::Escape("test=$test")) {
        throw "Voice integration result omitted $test. See $instrumentationLog"
    }
}

& (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
    -Phase Finish -Serial $Serial -PackageName $package `
    -OutputDirectory $appAttributionRoot -NetworkExpectation Zero `
    -Canary @(
        'xin chào',
        'xin chào khách hàng'
    )
& (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
    -Phase Finish -Serial $Serial -PackageName $testPackage `
    -OutputDirectory $providerAttributionRoot -NetworkExpectation Zero `
    -Canary @(
        'xin chào',
        'xin chào khách hàng'
    )
$appAttribution = Get-Content -Raw -LiteralPath `
    (Join-Path $appAttributionRoot 'summary.json') | ConvertFrom-Json
$providerAttribution = Get-Content -Raw -LiteralPath `
    (Join-Path $providerAttributionRoot 'summary.json') | ConvertFrom-Json

[ordered]@{
    generatedAt = (Get-Date).ToString('o')
    overall = 'PASS'
    serial = $Serial
    api = [int]((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '').Trim()
    model = ((Adb '-s' $Serial shell getprop ro.product.model) -join '').Trim()
    provider = 'debug-only explicit RecognitionService through Android SpeechRecognizer binder'
    productionProviderUntouched = $true
    testCount = $expectedTests.Count
    cases = $expectedTests
    lifecycle = @('Listening(partial)', 'Processing', 'Result(review required)')
    mappedErrors = @('Network', 'NoMatch')
    lateResultAfterCancel = $false
    networkAccountingSupported = [bool]$appAttribution.networkAccountingSupported -and
        [bool]$providerAttribution.networkAccountingSupported
    appUidRxDelta = [long]$appAttribution.rxDelta
    appUidTxDelta = [long]$appAttribution.txDelta
    providerUidRxDelta = [long]$providerAttribution.rxDelta
    providerUidTxDelta = [long]$providerAttribution.txDelta
    controlledTranscriptOccurrencesInUidLog = `
        [int]$appAttribution.canaryOccurrencesInUidLog +
        [int]$providerAttribution.canaryOccurrencesInUidLog
    instrumentationLog = $instrumentationLog
    appAttributionSummary = Join-Path $appAttributionRoot 'summary.json'
    providerAttributionSummary = Join-Path $providerAttributionRoot 'summary.json'
    claimBoundary = 'deterministic app-side provider integration; real speech/audio quality and provider policy still require physical acceptance'
} | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 `
    (Join-Path $OutputDirectory 'summary.json')

Write-Host "Deterministic voice-recognition integration passed: $OutputDirectory"
