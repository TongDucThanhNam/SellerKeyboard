[CmdletBinding()]
param(
    [string]$Serial = "",
    [string]$OutputDirectory = ""
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
} else {
    "adb"
}
$sellerIme = "com.terasumi.sellerkeyboard/.service.SellerKeyboard"
$gboardIme = "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
$editorActivity = "com.terasumi.sellerkeyboard/.benchmark.ImeBenchmarkActivity"
$talkBackService = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
$talkBackPackage = "com.google.android.marvin.talkback"

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $output = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: adb $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return $output
}

function Get-Setting {
    param([string]$Namespace, [string]$Name)
    return ((Invoke-Adb '-s' $Serial shell settings get $Namespace $Name) -join "`n").Trim()
}

function Restore-Setting {
    param([string]$Namespace, [string]$Name, [string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -eq "null") {
        Invoke-Adb '-s' $Serial shell settings delete $Namespace $Name | Out-Null
    } else {
        Invoke-Adb '-s' $Serial shell settings put $Namespace $Name $Value | Out-Null
    }
}

function Set-RefreshRate {
    param([int]$Hertz)
    Invoke-Adb '-s' $Serial shell settings put system peak_refresh_rate $Hertz | Out-Null
    Invoke-Adb '-s' $Serial shell settings put system min_refresh_rate $Hertz | Out-Null
    Invoke-Adb '-s' $Serial shell settings put system user_refresh_rate $Hertz | Out-Null
    Invoke-Adb '-s' $Serial shell settings put secure miui_refresh_rate $Hertz | Out-Null
    Invoke-Adb '-s' $Serial shell settings put secure user_refresh_rate $Hertz | Out-Null
    Invoke-Adb '-s' $Serial shell settings put global miui_refresh_rate $Hertz | Out-Null
    Invoke-Adb '-s' $Serial shell settings put global user_refresh_rate $Hertz | Out-Null
}

function Set-NightMode {
    param([ValidateSet("yes", "no", "auto")][string]$Mode)
    Invoke-Adb '-s' $Serial shell cmd uimode night $Mode | Out-Null
}

function Capture-Variant {
    param(
        [string]$Name,
        [int]$InputType = 0,
        [int]$ImeOptions = 6
    )

    # Do not force-stop the package between variants: the Activity and IME share
    # a process, and MIUI may select its fallback IME while that process is down.
    # Configuration changes recreate the Activity naturally; reselect explicitly.
    Invoke-Adb '-s' $Serial shell ime enable $sellerIme | Out-Null
    Invoke-Adb '-s' $Serial shell ime set $sellerIme | Out-Null
    $startArguments = @(
        '-s', $Serial, 'shell', 'am', 'start', '-W', '-f', '0x20000000',
        '-n', $editorActivity
    )
    if ($InputType -ne 0) {
        $startArguments += @('--ei', 'inputType', "$InputType", '--ei', 'imeOptions', "$ImeOptions")
    }
    Invoke-Adb @startArguments | Out-Null
    Start-Sleep -Seconds 2
    # Some OEM builds ignore the activity's first programmatic request while the
    # freshly selected IME service is binding. Retry a real editor tap, which is
    # the public user-equivalent request, and still fail closed after three tries.
    $inputMethodState = ""
    foreach ($attempt in 1..3) {
        Invoke-Adb '-s' $Serial shell input tap 540 500 | Out-Null
        Start-Sleep -Seconds 2
        $inputMethodState = (Invoke-Adb '-s' $Serial shell dumpsys input_method) -join "`n"
        if ($inputMethodState -match 'mInputShown=true') {
            break
        }
    }

    $selectedIme = ((Invoke-Adb '-s' $Serial shell settings get secure default_input_method) -join "`n").Trim()
    if ($selectedIme -ne $sellerIme) {
        throw "Variant $Name selected $selectedIme instead of $sellerIme"
    }
    if ($inputMethodState -notmatch 'mInputShown=true') {
        throw "Variant $Name did not show SellerKeyboard"
    }
    # Insets visibility precedes the first Compose draw on some MIUI builds.
    Start-Sleep -Seconds 3

    $remotePng = "/sdcard/sellerkeyboard-$Name.png"
    $remoteXml = "/sdcard/sellerkeyboard-$Name.xml"
    $localPng = Join-Path $OutputDirectory "$Name.png"
    $localXml = Join-Path $OutputDirectory "$Name.xml"
    Invoke-Adb '-s' $Serial shell screencap '-p' $remotePng | Out-Null
    Invoke-Adb '-s' $Serial pull $remotePng $localPng | Out-Null
    # UIAutomator output is useful accessibility evidence, but some TalkBack
    # versions transiently reject a dump while announcing a new window.
    & $adb -s $Serial shell uiautomator dump $remoteXml 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Invoke-Adb '-s' $Serial pull $remoteXml $localXml | Out-Null
    }
    Invoke-Adb '-s' $Serial shell rm '-f' $remotePng $remoteXml 2>&1 | Out-Null

    @(
        "variant=$Name"
        "requestedInputType=$InputType"
        "requestedImeOptions=$ImeOptions"
        "selectedIme=$selectedIme"
        "fontScale=$(Get-Setting system font_scale)"
        "nightMode=$(((Invoke-Adb '-s' $Serial shell cmd uimode night) -join ' ').Trim())"
        "rotation=$(Get-Setting system user_rotation)"
        "navigationMode=$(Get-Setting secure navigation_mode)"
        "accessibilityEnabled=$(Get-Setting secure accessibility_enabled)"
        "accessibilityServices=$(Get-Setting secure enabled_accessibility_services)"
        "windowAnimation=$(Get-Setting global window_animation_scale)"
        "transitionAnimation=$(Get-Setting global transition_animation_scale)"
        "animatorDuration=$(Get-Setting global animator_duration_scale)"
        "peakRefresh=$(Get-Setting system peak_refresh_rate)"
        "minRefresh=$(Get-Setting system min_refresh_rate)"
        "userRefresh=$(Get-Setting system user_refresh_rate)"
        "secureMiuiRefresh=$(Get-Setting secure miui_refresh_rate)"
        "secureUserRefresh=$(Get-Setting secure user_refresh_rate)"
        "globalMiuiRefresh=$(Get-Setting global miui_refresh_rate)"
        "globalUserRefresh=$(Get-Setting global user_refresh_rate)"
        "display=$(((Invoke-Adb '-s' $Serial shell dumpsys display | Select-String -Pattern 'mActiveModeId=|DisplayModeRecord') -join ' | ').Trim())"
        "inputMethod=$((($inputMethodState | Select-String -Pattern 'mCurMethodId=|mInputShown=|mWindowVisible=') -join ' | ').Trim())"
    ) | Set-Content -Encoding utf8 (Join-Path $OutputDirectory "$Name.txt")
}

$devices = @(Invoke-Adb devices | Where-Object { $_ -match "\tdevice$" })
if ([string]::IsNullOrWhiteSpace($Serial)) {
    if ($devices.Count -ne 1) {
        throw "Specify -Serial when exactly one authorized device is not connected."
    }
    $Serial = ($devices[0] -split "\t")[0]
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repositoryRoot "docs\evidence\device-2026-07-11-api33-matrix"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$original = [ordered]@{
    defaultIme = Get-Setting secure default_input_method
    fontScale = Get-Setting system font_scale
    peakRefresh = Get-Setting system peak_refresh_rate
    minRefresh = Get-Setting system min_refresh_rate
    userRefresh = Get-Setting system user_refresh_rate
    secureMiuiRefresh = Get-Setting secure miui_refresh_rate
    secureUserRefresh = Get-Setting secure user_refresh_rate
    globalMiuiRefresh = Get-Setting global miui_refresh_rate
    globalUserRefresh = Get-Setting global user_refresh_rate
    accelerometerRotation = Get-Setting system accelerometer_rotation
    userRotation = Get-Setting system user_rotation
    windowAnimation = Get-Setting global window_animation_scale
    transitionAnimation = Get-Setting global transition_animation_scale
    animatorDuration = Get-Setting global animator_duration_scale
    accessibilityEnabled = Get-Setting secure accessibility_enabled
    accessibilityServices = Get-Setting secure enabled_accessibility_services
    nightMode = (((Invoke-Adb '-s' $Serial shell cmd uimode night) -join " ").Trim())
}
$original | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $OutputDirectory "original-device-state.json")

try {
    Invoke-Adb '-s' $Serial shell settings put system accelerometer_rotation 0 | Out-Null
    Invoke-Adb '-s' $Serial shell settings put system user_rotation 0 | Out-Null
    Invoke-Adb '-s' $Serial shell settings put system font_scale 1.0 | Out-Null
    Set-NightMode yes
    Set-RefreshRate 60
    Capture-Variant "portrait-dark-60hz-font1"

    Invoke-Adb '-s' $Serial shell settings put system user_rotation 1 | Out-Null
    Capture-Variant "landscape-dark-60hz-font1"

    Invoke-Adb '-s' $Serial shell settings put system user_rotation 0 | Out-Null
    Set-NightMode no
    Capture-Variant "portrait-light-60hz-font1"

    Set-NightMode yes
    Set-RefreshRate 90
    Capture-Variant "portrait-dark-90hz-font1"

    Capture-Variant "editor-email-dark-90hz" -InputType 33
    Capture-Variant "editor-uri-dark-90hz" -InputType 17
    Capture-Variant "editor-phone-dark-90hz" -InputType 3
    Capture-Variant "editor-signed-decimal-dark-90hz" -InputType 12290
    Capture-Variant "editor-password-dark-90hz" -InputType 129

    Invoke-Adb '-s' $Serial shell settings put system font_scale 1.3 | Out-Null
    Capture-Variant "portrait-dark-90hz-font1_3"

    Invoke-Adb '-s' $Serial shell settings put system font_scale 2.0 | Out-Null
    Capture-Variant "portrait-dark-90hz-font2"

    Invoke-Adb '-s' $Serial shell settings put system font_scale 1.0 | Out-Null
    Invoke-Adb '-s' $Serial shell settings put global window_animation_scale 0 | Out-Null
    Invoke-Adb '-s' $Serial shell settings put global transition_animation_scale 0 | Out-Null
    Invoke-Adb '-s' $Serial shell settings put global animator_duration_scale 0 | Out-Null
    Capture-Variant "portrait-dark-90hz-reduced-motion"

    Invoke-Adb '-s' $Serial shell settings put secure enabled_accessibility_services $talkBackService | Out-Null
    Invoke-Adb '-s' $Serial shell settings put secure accessibility_enabled 1 | Out-Null
    Start-Sleep -Seconds 3
    Capture-Variant "portrait-dark-90hz-talkback"
} finally {
    Restore-Setting secure enabled_accessibility_services $original.accessibilityServices
    Restore-Setting secure accessibility_enabled $original.accessibilityEnabled
    if ($original.accessibilityEnabled -ne "1") {
        # Enabling TalkBack can launch its first-run activity even after the
        # service setting is restored. Stop that test-only onboarding state so
        # it cannot intercept later device dialogs such as OEM APK approval.
        Invoke-Adb '-s' $Serial shell am force-stop $talkBackPackage | Out-Null
    }
    Restore-Setting global window_animation_scale $original.windowAnimation
    Restore-Setting global transition_animation_scale $original.transitionAnimation
    Restore-Setting global animator_duration_scale $original.animatorDuration
    Restore-Setting system font_scale $original.fontScale
    Restore-Setting system peak_refresh_rate $original.peakRefresh
    Restore-Setting system min_refresh_rate $original.minRefresh
    Restore-Setting system user_refresh_rate $original.userRefresh
    Restore-Setting secure miui_refresh_rate $original.secureMiuiRefresh
    Restore-Setting secure user_refresh_rate $original.secureUserRefresh
    Restore-Setting global miui_refresh_rate $original.globalMiuiRefresh
    Restore-Setting global user_refresh_rate $original.globalUserRefresh
    Restore-Setting system accelerometer_rotation $original.accelerometerRotation
    Restore-Setting system user_rotation $original.userRotation
    if ($original.nightMode -match 'yes') {
        Set-NightMode yes
    } elseif ($original.nightMode -match 'no') {
        Set-NightMode no
    } else {
        Set-NightMode auto
    }
    if (-not [string]::IsNullOrWhiteSpace($original.defaultIme) -and $original.defaultIme -ne "null") {
        Invoke-Adb '-s' $Serial shell ime set $original.defaultIme | Out-Null
    } else {
        Invoke-Adb '-s' $Serial shell ime set $gboardIme | Out-Null
    }
}

Write-Host "Device matrix captured under $OutputDirectory"
