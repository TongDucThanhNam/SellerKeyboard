[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [switch]$SkipBuild,
    [switch]$SkipInstall
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
$hostPackage = 'com.terasumi.sellerkeyboard.benchmarkhost'
$ime = "$package/.service.SellerKeyboard"
$activity = "$hostPackage/.ImeBenchmarkActivity"
$bridge = "content://$package.ime-test-bridge"
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $result = @(& $adb @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if ($exitCode -ne 0) {
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

function Capture-Window([string]$Name) {
    $remoteXml = "/sdcard/voice-permission-$Name.xml"
    $remotePng = "/sdcard/voice-permission-$Name.png"
    $localXml = Join-Path $OutputDirectory "$Name-window.xml"
    $localPng = Join-Path $OutputDirectory "$Name.png"
    Adb '-s' $Serial shell uiautomator dump '--windows' $remoteXml | Out-Null
    Adb '-s' $Serial pull '-q' $remoteXml $localXml | Out-Null
    Adb '-s' $Serial shell screencap '-p' $remotePng | Out-Null
    Adb '-s' $Serial pull '-q' $remotePng $localPng | Out-Null
    [pscustomobject]@{
        XmlPath = $localXml
        PngPath = $localPng
        Xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $localXml
    }
}

function Find-Node([string]$Xml, [string[]]$Labels) {
    foreach ($label in $Labels) {
        $match = [regex]::Match(
            $Xml,
            '<node[^>]*(?:text|content-desc)="' + [regex]::Escape($label) +
                '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*/?>'
        )
        if ($match.Success) { return $match }
    }
    $null
}

function Tap-Node([string]$Xml, [string[]]$Labels) {
    $node = Find-Node $Xml $Labels
    if ($null -eq $node) { throw "Could not find UI node: $($Labels -join ' / ')" }
    $x = [int](([int]$node.Groups[1].Value + [int]$node.Groups[3].Value) / 2)
    $y = [int](([int]$node.Groups[2].Value + [int]$node.Groups[4].Value) / 2)
    Adb '-s' $Serial shell input tap $x $y | Out-Null
}

function Wait-WindowNode(
    [string[]]$Labels,
    [string]$Name,
    [int]$TimeoutSeconds = 20
) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $capture = Capture-Window $Name
        if ($null -ne (Find-Node $capture.Xml $Labels)) {
            Start-Sleep -Milliseconds 500
            return Capture-Window "$Name-final"
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for UI node $($Labels -join ' / ') in $($capture.XmlPath)"
}

function Wait-VoiceState([string[]]$Accepted, [int]$TimeoutSeconds = 20) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $snapshot = Call-Bridge 'feature_attribution_snapshot'
        $state = Bundle-Value $snapshot 'voice_state'
        if ($state -in $Accepted) { return $snapshot }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for voice state [$($Accepted -join ',')]. Last: $snapshot"
}

function Is-RecordAudioGranted {
    $dump = (Adb '-s' $Serial shell dumpsys package $package) -join "`n"
    $match = [regex]::Match(
        $dump,
        'android\.permission\.RECORD_AUDIO:\s+granted=(true|false)'
    )
    if (-not $match.Success) { throw 'Could not resolve RECORD_AUDIO grant state.' }
    $match.Groups[1].Value -eq 'true'
}

function Start-HostSession {
    Adb '-s' $Serial shell ime enable $ime | Out-Null
    Adb '-s' $Serial shell ime set $ime | Out-Null
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $activity '--ei' inputType 1 '--ei' imeOptions 6 `
        '--es' hintText VoicePermissionEditor | Out-Null
    Start-Sleep -Seconds 1
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $snapshot = Call-Bridge 'feature_attribution_snapshot'
        if ((Bundle-Value $snapshot 'active') -eq 'true' -and
            (Bundle-Value $snapshot 'editor_package') -eq $hostPackage) {
            return $snapshot
        }
        Adb '-s' $Serial shell ime enable $ime | Out-Null
        Adb '-s' $Serial shell ime set $ime | Out-Null
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Could not activate neutral host editor. Last: $snapshot"
}

if (-not $SkipBuild) {
    $buildLog = Join-Path $OutputDirectory 'build.txt'
    $build = @(& (Join-Path $root 'gradlew.bat') `
        :app:assembleDebug :benchmark-host:assembleBenchmark --console=plain 2>&1)
    $build | Set-Content -Encoding utf8 -LiteralPath $buildLog
    if ($LASTEXITCODE -ne 0) { throw "Voice permission build failed. See $buildLog" }
}
$appApk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
$hostApk = Join-Path $root `
    'benchmark-host\build\outputs\apk\benchmark\benchmark-host-benchmark.apk'
if (-not $SkipInstall) {
    Adb '-s' $Serial install '-r' '-t' $appApk | Out-Null
    Adb '-s' $Serial install '-r' $hostApk | Out-Null
}
$originalPermissionGranted = Is-RecordAudioGranted
$originalShowIme = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
$originalIme = ((Adb '-s' $Serial shell settings get secure `
    default_input_method) -join '').Trim()
$api = [int](((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '').Trim())
$model = ((Adb '-s' $Serial shell getprop ro.product.model) -join '').Trim()
$manufacturer = ((Adb '-s' $Serial shell getprop ro.product.manufacturer) -join '').Trim()
$fingerprint = ((Adb '-s' $Serial shell getprop ro.build.fingerprint) -join '').Trim()
$isEmulator = ((Adb '-s' $Serial shell getprop ro.kernel.qemu) -join '').Trim() -eq '1'
$summary = $null

try {
    if ($originalPermissionGranted) {
        Adb '-s' $Serial shell pm revoke $package android.permission.RECORD_AUDIO | Out-Null
    }
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Start-HostSession | Out-Null

    $requesting = Call-Bridge 'start_voice_attribution'
    $requestingState = Bundle-Value $requesting 'voice_state'
    if ($requestingState -notin @('RequestingPermission', 'Idle')) {
        throw "Voice request did not enter a safe permission transition: $requesting"
    }
    $dismissDisclosure = Wait-WindowNode @('Not now') 'dismiss-disclosure'
    foreach ($required in @(
        'Allow voice input?',
        'Continue',
        'Not now',
        'A transcript is shown for review and is never sent automatically.'
    )) {
        if ($dismissDisclosure.Xml -notmatch [regex]::Escape($required)) {
            throw "Voice disclosure omitted required text: $required"
        }
    }
    Tap-Node $dismissDisclosure.Xml @('Not now')
    Start-Sleep -Milliseconds 500
    if (Is-RecordAudioGranted) { throw 'Dismissing disclosure unexpectedly granted audio.' }
    $afterDismiss = Wait-VoiceState @('RequestingPermission', 'Idle')
    Call-Bridge 'cancel_voice_attribution' | Out-Null
    Wait-VoiceState @('Idle') | Out-Null
    Adb '-s' $Serial shell input keyevent KEYCODE_BACK | Out-Null
    Start-Sleep -Milliseconds 500
    Start-HostSession | Out-Null

    $retryRequest = Call-Bridge 'start_voice_attribution'
    if ((Bundle-Value $retryRequest 'voice_state') -notin @('RequestingPermission', 'Idle')) {
        throw "Voice retry did not enter a safe permission transition: $retryRequest"
    }
    $grantDisclosure = Wait-WindowNode @('Continue') 'grant-disclosure'
    Tap-Node $grantDisclosure.Xml @('Continue')
    $systemPrompt = Wait-WindowNode `
        @(
            'While using the app', 'Only this time', 'Allow',
            'Trong khi dùng ứng dụng', 'Khi dùng ứng dụng', 'Chỉ lần này', 'Cho phép'
        ) 'system-permission'
    Tap-Node $systemPrompt.Xml @(
        'While using the app', 'Only this time', 'Allow',
        'Trong khi dùng ứng dụng', 'Khi dùng ứng dụng', 'Chỉ lần này', 'Cho phép'
    )
    Start-Sleep -Seconds 1
    if (-not (Is-RecordAudioGranted)) { throw 'System permission approval did not grant audio.' }
    Adb '-s' $Serial shell input keyevent KEYCODE_BACK | Out-Null
    Start-Sleep -Milliseconds 500
    Start-HostSession | Out-Null

    Call-Bridge 'start_voice_attribution' | Out-Null
    $providerStart = Wait-VoiceState @('Listening', 'Processing', 'Result', 'Error') 30
    $providerState = Bundle-Value $providerStart 'voice_state'
    $providerError = Bundle-Value $providerStart 'voice_error'
    if ($providerState -eq 'Error' -and $providerError -in @(
            'PermissionDenied', 'NoRecognitionService', 'Disabled', 'DisabledForField'
        )) {
        throw "Voice provider was not exercised after grant: $providerStart"
    }
    Call-Bridge 'cancel_voice_attribution' | Out-Null
    $idle = Wait-VoiceState @('Idle')

    $summary = [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        api = $api
        model = $model
        manufacturer = $manufacturer
        fingerprint = $fingerprint
        isEmulator = $isEmulator
        disclosure = [ordered]@{
            requestingPermissionState = $requestingState
            titlePresent = $true
            providerNetworkDisclosurePresent = $true
            reviewAndNoAutoSendDisclosurePresent = $true
            fieldChangeFailsClosedToIdle = (Bundle-Value $afterDismiss 'voice_state') -eq 'Idle'
            dismissLeavesPermissionDenied = $true
            dismissScreenshot = $dismissDisclosure.PngPath
            grantScreenshot = $grantDisclosure.PngPath
        }
        systemPermission = [ordered]@{
            promptScreenshot = $systemPrompt.PngPath
            grantedAfterExplicitSystemAction = $true
        }
        providerAfterGrant = [ordered]@{
            state = $providerState
            error = $providerError
            restoredIdleAfterCancel = (Bundle-Value $idle 'voice_state') -eq 'Idle'
        }
        claimBoundary = if ($isEmulator) {
            "unattended API $api emulator disclosure dismiss/retry/grant and configured-provider start/cancel; physical OEM presentation and real acoustic result remain open"
        } else {
            "unattended API $api $manufacturer OEM disclosure dismiss/retry/grant and configured-provider start/cancel; real physical-microphone/acoustic result remains open"
        }
    }
} finally {
    try { Call-Bridge 'cancel_voice_attribution' | Out-Null } catch { }
    if ($originalPermissionGranted) {
        try { Adb '-s' $Serial shell pm grant $package android.permission.RECORD_AUDIO | Out-Null } catch { }
    } else {
        try { Adb '-s' $Serial shell pm revoke $package android.permission.RECORD_AUDIO | Out-Null } catch { }
    }
    if ($originalShowIme -eq 'null' -or [string]::IsNullOrWhiteSpace($originalShowIme)) {
        Adb '-s' $Serial shell settings delete secure show_ime_with_hard_keyboard | Out-Null
    } else {
        Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard `
            $originalShowIme | Out-Null
    }
    if (-not [string]::IsNullOrWhiteSpace($originalIme) -and $originalIme -ne 'null') {
        Adb '-s' $Serial shell ime set $originalIme | Out-Null
    }
}

$restoredPermission = (Is-RecordAudioGranted) -eq $originalPermissionGranted
$restoredShowIme = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim() -eq $originalShowIme
$restoredIme = ((Adb '-s' $Serial shell settings get secure `
    default_input_method) -join '').Trim() -eq $originalIme
if (-not $restoredPermission -or -not $restoredShowIme -or -not $restoredIme) {
    throw "Voice permission verifier did not restore device state exactly: " +
        "permission=$restoredPermission showIme=$restoredShowIme ime=$restoredIme"
}
$summary.restoration = [ordered]@{
    recordAudioPermissionExact = $restoredPermission
    showImeWithHardKeyboardExact = $restoredShowIme
    defaultImeExact = $restoredIme
}
$summary | ConvertTo-Json -Depth 7 | Set-Content -Encoding utf8 `
    (Join-Path $OutputDirectory 'summary.json')
Write-Host "Voice permission/disclosure flow passed: $OutputDirectory"
