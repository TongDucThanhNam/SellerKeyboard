[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' } else { 'adb' }
$package = 'com.terasumi.sellerkeyboard'
$activity = "$package/.HomeActivity"
$bridge = "content://$package.cloud-sync-test-bridge"
$titleCanary = 'Connected local-only sync boundary'
$contentCanary = 'Connected template must remain local'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: $($Arguments -join ' ')`n$($result -join "`n")"
    }
    $result
}

function Call-Bridge([string]$Method) {
    ((Adb '-s' $Serial shell content call --uri $bridge --method $Method) -join "`n").Trim()
}

function Bundle-Value([string]$Bundle, [string]$Key) {
    $match = [regex]::Match($Bundle, "(?:\{|,\s*)$([regex]::Escape($Key))=([^,}]*)")
    if (-not $match.Success) { return $null }
    $match.Groups[1].Value.Trim()
}

function Assert-CloudCold([string]$Snapshot, [string]$Stage) {
    foreach ($key in @('client_initialized', 'auth_initialized', 'backup_initialized')) {
        if ((Bundle-Value $Snapshot $key) -ne 'false') {
            throw "Cloud dependency '$key' initialized during ${Stage}: $Snapshot"
        }
    }
}

$attributionRoot = Join-Path $OutputDirectory 'network-attribution'
$seeded = $false
try {
    $before = Call-Bridge 'snapshot'
    Assert-CloudCold $before 'preflight'
    $seed = Call-Bridge 'seed'
    $seeded = $true
    Assert-CloudCold $seed 'local seed'
    if ((Bundle-Value $seed local_snippet_present) -ne 'true') {
        throw "Controlled local snippet was not seeded: $seed"
    }

    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Start -Serial $Serial -PackageName $package `
        -OutputDirectory $attributionRoot -NetworkExpectation Zero
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '-n' $activity | Out-Null
    Start-Sleep -Seconds 2
    $remoteXml = '/sdcard/seller-sync-release-boundary.xml'
    $localXml = Join-Path $OutputDirectory 'home-window.xml'
    Adb '-s' $Serial shell uiautomator dump $remoteXml | Out-Null
    Adb '-s' $Serial pull '-q' $remoteXml $localXml | Out-Null
    $window = Get-Content -Raw -LiteralPath $localXml
    $cloudLabelsAbsent = $window -notmatch 'Cloud backup|Backup now|Restore backup'
    if (-not $cloudLabelsAbsent) {
        throw "Deferred cloud controls appeared in the production Home route: $localXml"
    }
    $after = Call-Bridge 'snapshot'
    Assert-CloudCold $after 'HomeActivity launch'
    if ((Bundle-Value $after local_snippet_present) -ne 'true') {
        throw "Controlled local snippet was lost after HomeActivity launch: $after"
    }
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Finish -Serial $Serial -PackageName $package `
        -OutputDirectory $attributionRoot -NetworkExpectation Zero `
        -Canary @($titleCanary, $contentCanary)
    $attribution = Get-Content -Raw -LiteralPath `
        (Join-Path $attributionRoot 'summary.json') | ConvertFrom-Json

    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        localSnippetPresent = $true
        cloudControlsAbsent = $cloudLabelsAbsent
        clientInitialized = $false
        authInitialized = $false
        backupInitialized = $false
        uidRxDelta = $attribution.rxDelta
        uidTxDelta = $attribution.txDelta
        controlledTextOccurrencesInUidLog = $attribution.canaryOccurrencesInUidLog
        claimBoundary = 'current release route defers cloud sync; future opt-in sync requires separate approval and credentialed attribution'
    } | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'summary.json')
    Write-Host "Sync release boundary passed: $OutputDirectory"
} finally {
    if ($seeded) {
        $restore = Call-Bridge 'restore'
        if ((Bundle-Value $restore restored) -ne 'true') {
            throw "Could not restore sync-boundary test data: $restore"
        }
    }
}
