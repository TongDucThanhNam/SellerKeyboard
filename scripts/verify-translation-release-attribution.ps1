[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' } else { 'adb' }
$package = 'com.terasumi.sellerkeyboard'
$providerPackage = 'com.google.android.gms'
$probe = "content://$package.translation-release-probe"
$canary = 'Don hang attribution rieng tu'
$apk = Join-Path $root 'app\build\outputs\apk\benchmark\app-benchmark.apk'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: $($Arguments -join ' ')`n$($result -join "`n")"
    }
    $result
}

function Call-Probe([string]$Method) {
    ((Adb '-s' $Serial shell content call --uri $probe --method $Method) -join "`n").Trim()
}

function Wait-Probe([string]$SuccessKey, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $snapshot = Call-Probe 'snapshot'
        if ($snapshot -match "(?:\{|,\s*)$SuccessKey=true(?:,|})") { return $snapshot }
        if ($snapshot -match '(?:\{|,\s*)error_present=true(?:,|})') {
            throw "Release translation probe failed: $snapshot"
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $SuccessKey in release translation probe: $snapshot"
}

function Start-Attribution([string]$PackageName, [string]$Directory) {
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Start -Serial $Serial -PackageName $PackageName `
        -OutputDirectory $Directory -NetworkExpectation Record
}

function Finish-Attribution([string]$PackageName, [string]$Directory) {
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Finish -Serial $Serial -PackageName $PackageName `
        -OutputDirectory $Directory -NetworkExpectation Record -Canary @($canary)
    Get-Content -Raw -LiteralPath (Join-Path $Directory 'summary.json') | ConvertFrom-Json
}

if (-not (Test-Path -LiteralPath $apk)) {
    throw "Non-debuggable benchmark APK is missing: $apk"
}

try {
    Adb '-s' $Serial install '-r' $apk | Out-Null
    Call-Probe 'delete' | Out-Null
    $deleteSnapshot = Wait-Probe model_deleted 60

    $imeRoot = Join-Path $OutputDirectory 'ime'
    $providerRoot = Join-Path $OutputDirectory 'provider'
    Start-Attribution $package $imeRoot
    Start-Attribution $providerPackage $providerRoot
    Call-Probe 'translate' | Out-Null
    $previewSnapshot = Wait-Probe preview 180
    if ($previewSnapshot -notmatch '(?:\{|,\s*)original_matches=true(?:,|})') {
        throw "Release translation preview did not preserve the controlled original: $previewSnapshot"
    }
    $imeAttribution = Finish-Attribution $package $imeRoot
    $providerAttribution = Finish-Attribution $providerPackage $providerRoot

    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        buildType = 'benchmark'
        debuggable = $false
        minified = $true
        modelDeleted = $true
        previewVerified = $true
        originalMatchedCanary = $true
        imeAttribution = $imeAttribution
        providerAttribution = $providerAttribution
        claimBoundary = 'non-debuggable minified release-like APK; benchmark-only shell probe'
    } | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'summary.json')
    Write-Host "Release-like translation attribution passed: $OutputDirectory"
} finally {
    try { Call-Probe 'cancel' | Out-Null } catch { }
}
