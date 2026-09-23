[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][ValidateSet('Seed', 'VerifyAndRestore', 'Restore')]
    [string]$Phase,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
} else {
    'adb'
}
$sellerPackage = 'com.terasumi.sellerkeyboard'
$hostPackage = 'com.terasumi.sellerkeyboard.benchmarkhost'
$sellerIme = "$sellerPackage/.service.SellerKeyboard"
$hostActivity = "$hostPackage/.ImeBenchmarkActivity"
$imeBridge = "content://$sellerPackage.ime-test-bridge"
$seedPath = Join-Path $OutputDirectory 'seed.json'
$summaryPath = Join-Path $OutputDirectory 'summary.json'
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
    ((Adb '-s' $Serial shell content call '--uri' $imeBridge '--method' $Method) -join "`n").Trim()
}

function Bundle-Value([string]$Bundle, [string]$Key) {
    $match = [regex]::Match($Bundle, "(?:\{|,\s*)$([regex]::Escape($Key))=([^,}]*)")
    if (-not $match.Success) { return $null }
    $match.Groups[1].Value.Trim()
}

function Assert-BundleTrue([string]$Bundle, [string]$Key, [string]$Context) {
    $actual = Bundle-Value $Bundle $Key
    if ($actual -ne 'true') {
        throw "$Context expected '$Key=true' but was '$actual'. Bundle: $Bundle"
    }
}

function Start-ImeSession {
    Adb '-s' $Serial shell am force-stop $hostPackage | Out-Null
    $enabledSetting = ((Adb '-s' $Serial shell settings get secure enabled_input_methods) -join '').Trim()
    $fallbackIme = $enabledSetting -split ':' | ForEach-Object {
        ($_ -split ';')[0]
    } | Where-Object {
        -not [string]::IsNullOrWhiteSpace($_) -and $_ -ne $sellerIme
    } | Select-Object -First 1
    if (-not [string]::IsNullOrWhiteSpace($fallbackIme)) {
        Adb '-s' $Serial shell ime set $fallbackIme | Out-Null
    }
    Adb '-s' $Serial shell ime enable $sellerIme | Out-Null
    Adb '-s' $Serial shell ime set $sellerIme | Out-Null
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $hostActivity | Out-Null
    $deadline = (Get-Date).AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 500
        $bundle = Call-Bridge 'snapshot'
        if ((Bundle-Value $bundle 'active') -eq 'true' -and
            (Bundle-Value $bundle 'editor_package') -eq $hostPackage) {
            return $bundle
        }
    } while ((Get-Date) -lt $deadline)
    throw "Timed out activating SellerKeyboard for upgrade verification. Last: $bundle"
}

function Get-PackageState {
    $paths = @(Adb '-s' $Serial shell pm path $sellerPackage)
    $basePath = ($paths | Where-Object { $_ -match 'base\.apk' } | Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($basePath)) {
        $basePath = $paths | Select-Object -First 1
    }
    $basePath = ([string]$basePath).Trim() -replace '^package:', ''
    if ([string]::IsNullOrWhiteSpace($basePath)) {
        throw "Could not resolve the installed base APK for $sellerPackage"
    }
    $digestLine = ((Adb '-s' $Serial shell sha256sum $basePath) -join '').Trim()
    $digestMatch = [regex]::Match($digestLine, '^([0-9a-fA-F]{64})\s+')
    if (-not $digestMatch.Success) {
        throw "Could not hash the installed base APK: $digestLine"
    }
    $dump = (Adb '-s' $Serial shell dumpsys package $sellerPackage) -join "`n"
    $versionCode = [regex]::Match($dump, 'versionCode=(\d+)').Groups[1].Value
    $versionName = [regex]::Match($dump, 'versionName=([^\r\n]+)').Groups[1].Value.Trim()
    [ordered]@{
        versionCode = [long]$versionCode
        versionName = $versionName
        installedBaseApk = $basePath
        installedBaseApkSha256 = $digestMatch.Groups[1].Value.ToLowerInvariant()
    }
}

function Get-DeviceIdentity {
    $qemu = ((Adb '-s' $Serial shell getprop ro.kernel.qemu) -join '').Trim()
    [ordered]@{
        serial = $Serial
        manufacturer = ((Adb '-s' $Serial shell getprop ro.product.manufacturer) -join '').Trim()
        model = ((Adb '-s' $Serial shell getprop ro.product.model) -join '').Trim()
        api = [int](((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '').Trim())
        buildFingerprint = ((Adb '-s' $Serial shell getprop ro.build.fingerprint) -join '').Trim()
        isEmulator = $qemu -eq '1'
    }
}

if ($Phase -eq 'Seed') {
    Start-ImeSession | Out-Null
    $seedBundle = Call-Bridge 'capture_and_seed_upgrade_fixture'
    $seedBundle | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'seed-bundle.txt')
    Assert-BundleTrue $seedBundle 'upgrade_fixture_captured' 'Upgrade fixture capture'
    Assert-BundleTrue $seedBundle 'upgrade_fixture_seeded' 'Upgrade fixture seed'
    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        device = Get-DeviceIdentity
        package = Get-PackageState
        originalStateSha256 = Bundle-Value $seedBundle 'upgrade_original_state_sha256'
        fixtureCaptured = $true
        fixtureSeeded = $true
    } | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 $seedPath
    Write-Host "Upgrade preservation fixture seeded: $seedPath"
    exit 0
}

if ($Phase -eq 'Restore') {
    Start-ImeSession | Out-Null
    $restoreBundle = Call-Bridge 'restore_upgrade_fixture'
    $restoreBundle | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'emergency-restore-bundle.txt')
    Assert-BundleTrue $restoreBundle 'upgrade_fixture_restored' 'Emergency upgrade fixture restore'
    Assert-BundleTrue $restoreBundle 'upgrade_original_state_exact' 'Emergency original-state restore'
    Write-Host 'Upgrade preservation fixture is restored.'
    exit 0
}

if (-not (Test-Path -LiteralPath $seedPath)) {
    throw "Upgrade seed evidence is missing: $seedPath"
}
$seed = Get-Content -Raw -LiteralPath $seedPath | ConvertFrom-Json
Start-ImeSession | Out-Null
$afterPackage = Get-PackageState
$verificationBundle = $null
$restoreBundle = $null
try {
    $verificationBundle = Call-Bridge 'upgrade_fixture_snapshot'
    $verificationBundle | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'post-upgrade-bundle.txt')
    foreach ($requirement in @(
        'upgrade_fixture_captured',
        'upgrade_legacy_snippet_exact',
        'upgrade_template_exact',
        'upgrade_image_url_preserved',
        'upgrade_pinned_clipboard_exact',
        'upgrade_settings_exact',
        'upgrade_export_valid',
        'upgrade_export_exact'
    )) {
        Assert-BundleTrue $verificationBundle $requirement 'Post-upgrade preservation'
    }
    $beforeHash = [string]$seed.package.installedBaseApkSha256
    if ($beforeHash -eq [string]$afterPackage.installedBaseApkSha256) {
        throw 'The installed base APK hash did not change across the attended replacement.'
    }
    if ([long]$afterPackage.versionCode -le [long]$seed.package.versionCode) {
        throw "The attended install was not a version-code upgrade: $($seed.package.versionCode) -> $($afterPackage.versionCode)."
    }
} finally {
    $restoreBundle = Call-Bridge 'restore_upgrade_fixture'
    $restoreBundle | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'restore-bundle.txt')
}
Assert-BundleTrue $restoreBundle 'upgrade_fixture_restored' 'Upgrade fixture restore'
Assert-BundleTrue $restoreBundle 'upgrade_original_state_exact' 'Original-state restore'
$device = Get-DeviceIdentity
[ordered]@{
    generatedAt = (Get-Date).ToString('o')
    overall = 'PASS'
    serial = $Serial
    manufacturer = $device.manufacturer
    model = $device.model
    api = $device.api
    buildFingerprint = $device.buildFingerprint
    isEmulator = [bool]$device.isEmulator
    packageReplacementObserved = `
        ([string]$seed.package.installedBaseApkSha256 -ne `
            [string]$afterPackage.installedBaseApkSha256)
    versionCodeUpgradeObserved = `
        ([long]$afterPackage.versionCode -gt [long]$seed.package.versionCode)
    beforePackage = $seed.package
    afterPackage = $afterPackage
    legacySnippetPreserved = $true
    imageUrlsPreserved = $true
    pinnedClipboardPreserved = $true
    keyboardSettingsPreserved = $true
    versionedExportValidated = $true
    versionedExportRoundTripExact = $true
    originalStateRestoredExactly = $true
    originalStateSha256 = [string]$seed.originalStateSha256
    exportSha256 = Bundle-Value $verificationBundle 'upgrade_export_sha256'
} | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 $summaryPath
Write-Host "Upgrade preservation evidence passed: $summaryPath"
