[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' } else { 'adb' }
$package = 'com.terasumi.sellerkeyboard'
$targetPackage = 'com.terasumi.sellerkeyboard.benchmarkhost'
$translationProviderPackage = 'com.google.android.gms'
$ime = "$package/.service.SellerKeyboard"
$activity = "$targetPackage/.ImeBenchmarkActivity"
$bridge = "content://$package.ime-test-bridge"
$voiceCanary = 'connectedvoicecontextcanary'
$translationCanary = 'Don hang attribution rieng tu'
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

function Wait-FeatureState {
    param(
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string[]]$Accepted,
        [int]$TimeoutSeconds = 20
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $snapshot = Call-Bridge 'feature_attribution_snapshot'
        $state = Bundle-Value $snapshot $Key
        if ($state -in $Accepted) { return $snapshot }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Key in [$($Accepted -join ', ')]. Last: $snapshot"
}

function Start-Attribution([string]$PackageName, [string]$Directory, [string]$Expectation) {
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Start -Serial $Serial -PackageName $PackageName `
        -OutputDirectory $Directory -NetworkExpectation $Expectation
}

function Finish-Attribution(
    [string]$PackageName,
    [string]$Directory,
    [string]$Expectation,
    [string]$Canary
) {
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Finish -Serial $Serial -PackageName $PackageName `
        -OutputDirectory $Directory -NetworkExpectation $Expectation -Canary @($Canary)
    Get-Content -Raw -LiteralPath (Join-Path $Directory 'summary.json') | ConvertFrom-Json
}

function Assert-PackageInstalled([string]$PackageName) {
    $output = (Adb '-s' $Serial shell pm list packages '-U' $PackageName) -join "`n"
    if ($output -notmatch "(?m)^package:$([regex]::Escape($PackageName))\s+uid:\d+\s*$") {
        throw "Required attributed package is not installed: $PackageName"
    }
}

$permissionDump = (Adb '-s' $Serial shell dumpsys package $package) -join "`n"
$permissionMatch = [regex]::Match(
    $permissionDump,
    'android\.permission\.RECORD_AUDIO:\s+granted=(true|false)'
)
if (-not $permissionMatch.Success) { throw 'Could not determine RECORD_AUDIO permission state.' }
$recordAudioOriginallyGranted = $permissionMatch.Groups[1].Value -eq 'true'
$originalShowImeWithHardKeyboard = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()

$voiceProviderComponent = ((Adb '-s' $Serial shell settings get secure `
    voice_recognition_service) -join '').Trim()
if ([string]::IsNullOrWhiteSpace($voiceProviderComponent) -or
    $voiceProviderComponent -eq 'null' -or $voiceProviderComponent -notmatch '/') {
    throw "No configured Android speech-recognition provider: $voiceProviderComponent"
}
$voiceProviderPackage = ($voiceProviderComponent -split '/', 2)[0]
Assert-PackageInstalled $voiceProviderPackage
Assert-PackageInstalled $translationProviderPackage

try {
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    $api = [int](((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '').Trim())
    if ($api -ge 30) { Adb '-s' $Serial shell ime reset | Out-Null }
    Adb '-s' $Serial shell ime enable $ime | Out-Null
    Adb '-s' $Serial shell ime set $ime | Out-Null
    if (-not $recordAudioOriginallyGranted) {
        Adb '-s' $Serial shell pm grant $package android.permission.RECORD_AUDIO | Out-Null
    }
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $activity '--ei' inputType 1 '--ei' imeOptions 6 `
        '--es' hintText FeatureAttributionEditor | Out-Null
    Start-Sleep -Seconds 1
    Adb '-s' $Serial shell input text $voiceCanary | Out-Null
    Start-Sleep -Milliseconds 500
    $ready = Wait-FeatureState -Key active -Accepted @('true')
    if ((Bundle-Value $ready sensitive) -ne 'false') {
        throw "Feature attribution requires a non-sensitive editor: $ready"
    }

    $voiceImeRoot = Join-Path $OutputDirectory 'voice\ime'
    $voiceProviderRoot = Join-Path $OutputDirectory 'voice\provider'
    Start-Attribution $package $voiceImeRoot Zero
    Start-Attribution $voiceProviderPackage $voiceProviderRoot Record
    Call-Bridge 'start_voice_attribution' | Out-Null
    $voiceSnapshot = Wait-FeatureState -Key voice_state `
        -Accepted @('Listening', 'Processing', 'Result', 'Error') -TimeoutSeconds 20
    $voiceState = Bundle-Value $voiceSnapshot voice_state
    $voiceError = Bundle-Value $voiceSnapshot voice_error
    if ($voiceState -eq 'Error' -and $voiceError -in @(
            'Disabled', 'DisabledForField', 'PermissionDenied', 'NoRecognitionService'
        )) {
        throw "Voice provider was not exercised: $voiceError"
    }
    $voiceUsingOnDevice = (Bundle-Value $voiceSnapshot voice_using_on_device) -eq 'true'
    Call-Bridge 'cancel_voice_attribution' | Out-Null
    $voiceFinal = Wait-FeatureState -Key voice_state -Accepted @('Idle')
    $voiceImeAttribution = Finish-Attribution $package $voiceImeRoot Zero $voiceCanary
    $voiceProviderAttribution = Finish-Attribution `
        $voiceProviderPackage $voiceProviderRoot Record $voiceCanary

    Call-Bridge 'delete_translation_model' | Out-Null
    $deleted = Wait-FeatureState -Key translation_state `
        -Accepted @('ModelDeleted', 'Error') -TimeoutSeconds 45
    if ((Bundle-Value $deleted translation_state) -ne 'ModelDeleted') {
        throw "Could not establish a clean translation-model baseline: $deleted"
    }

    $translationImeRoot = Join-Path $OutputDirectory 'translation\ime'
    $translationProviderRoot = Join-Path $OutputDirectory 'translation\provider'
    Start-Attribution $package $translationImeRoot Record
    Start-Attribution $translationProviderPackage $translationProviderRoot Record
    Call-Bridge 'start_translation_attribution' | Out-Null
    $translationSnapshot = Wait-FeatureState -Key translation_state `
        -Accepted @('Preview', 'Error') -TimeoutSeconds 180
    $translationState = Bundle-Value $translationSnapshot translation_state
    if ($translationState -ne 'Preview' -or
        (Bundle-Value $translationSnapshot translation_original_matches_canary) -ne 'true') {
        throw "Connected translation did not reach a verified preview: $translationSnapshot"
    }
    $translationImeAttribution = Finish-Attribution `
        $package $translationImeRoot Record $translationCanary
    $translationProviderAttribution = Finish-Attribution `
        $translationProviderPackage $translationProviderRoot Record $translationCanary
    Call-Bridge 'cancel_translation_attribution' | Out-Null
    $translationFinal = Wait-FeatureState -Key translation_state -Accepted @('Idle')

    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        voice = [ordered]@{
            providerComponent = $voiceProviderComponent
            providerPackage = $voiceProviderPackage
            stateAfterStart = $voiceState
            errorAfterStart = $voiceError
            usingOnDevice = $voiceUsingOnDevice
            restoredIdle = (Bundle-Value $voiceFinal voice_state) -eq 'Idle'
            imeAttribution = $voiceImeAttribution
            providerAttribution = $voiceProviderAttribution
            claimBoundary = 'real provider start/cancel on headless emulator; no deterministic spoken transcript'
        }
        translation = [ordered]@{
            providerPackage = $translationProviderPackage
            finalState = $translationState
            originalMatchedCanary = $true
            restoredIdle = (Bundle-Value $translationFinal translation_state) -eq 'Idle'
            imeAttribution = $translationImeAttribution
            providerAttribution = $translationProviderAttribution
            claimBoundary = 'real ML Kit VI-to-EN model deletion, explicit download, and preview'
        }
    } | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'summary.json')
    Write-Host "Voice/translation attribution passed: $OutputDirectory"
} finally {
    try { Call-Bridge 'cancel_voice_attribution' | Out-Null } catch { }
    try { Call-Bridge 'cancel_translation_attribution' | Out-Null } catch { }
    if (-not $recordAudioOriginallyGranted) {
        Adb '-s' $Serial shell pm revoke $package android.permission.RECORD_AUDIO | Out-Null
    }
    if ($originalShowImeWithHardKeyboard -eq 'null' -or
        [string]::IsNullOrWhiteSpace($originalShowImeWithHardKeyboard)) {
        Adb '-s' $Serial shell settings delete secure show_ime_with_hard_keyboard | Out-Null
    } else {
        Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard `
            $originalShowImeWithHardKeyboard | Out-Null
    }
}
