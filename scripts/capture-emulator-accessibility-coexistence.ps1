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
$sellerPackage = "com.terasumi.sellerkeyboard"
$sellerIme = "$sellerPackage/.service.SellerKeyboard"
$targetPackage = "com.terasumi.sellerkeyboard.benchmarkhost"
$targetActivity = "$targetPackage/.ImeBenchmarkActivity"
$talkBackPackage = "com.google.android.marvin.talkback"
$talkBackService = "$talkBackPackage/.TalkBackService"
$switchAccessPackage = "com.google.android.accessibility.switchaccess"
$switchAccessService = "$switchAccessPackage/com.android.switchaccess.SwitchAccessService"
$notificationPermission = "android.permission.POST_NOTIFICATIONS"
$canary = "vxz"

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: adb $($Arguments -join ' ')`n$($result -join "`n")"
    }
    $result
}

function Get-Setting([string]$Namespace, [string]$Name) {
    ((Adb '-s' $Serial shell settings get $Namespace $Name) -join "").Trim()
}

function Restore-Setting([string]$Namespace, [string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -eq "null") {
        Adb '-s' $Serial shell settings delete $Namespace $Name | Out-Null
    } else {
        Adb '-s' $Serial shell settings put $Namespace $Name $Value | Out-Null
    }
}

function Permission-State([string]$PackageName) {
    $dump = (Adb '-s' $Serial shell dumpsys package $PackageName) -join "`n"
    $match = [regex]::Match(
        $dump,
        [regex]::Escape($notificationPermission) + ': granted=(true|false)'
    )
    if (-not $match.Success) { return 'not-requested' }
    if ($match.Groups[1].Value -eq 'true') { return 'granted' }
    'denied'
}

function Package-Stopped([string]$PackageName) {
    $dump = (Adb '-s' $Serial shell dumpsys package $PackageName) -join "`n"
    [regex]::IsMatch($dump, 'User 0:[^\r\n]*stopped=true')
}

function Dump-Window([string]$Name) {
    $remote = "/sdcard/accessibility-$Name.xml"
    $local = Join-Path $OutputDirectory "$Name-window.xml"
    foreach ($attempt in 1..4) {
        & $adb -s $Serial shell uiautomator dump '--windows' $remote 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Adb '-s' $Serial pull '-q' $remote $local | Out-Null
            return Get-Content -Raw -Encoding UTF8 -LiteralPath $local
        }
        Start-Sleep -Seconds 1
    }
    throw "UIAutomator could not capture $Name after four attempts"
}

function Capture-Screen([string]$Name) {
    $remote = "/sdcard/accessibility-$Name.png"
    $local = Join-Path $OutputDirectory "$Name.png"
    Adb '-s' $Serial shell screencap '-p' $remote | Out-Null
    Adb '-s' $Serial pull '-q' $remote $local | Out-Null
}

function Find-Node([string]$Xml, [string]$Attribute, [string]$Value) {
    $escaped = [regex]::Escape($Value)
    foreach ($node in [regex]::Matches($Xml, '<node[^>]*/?>')) {
        if ($node.Value -match "$Attribute=`"$escaped`"") { return $node }
    }
    $null
}

function Tap-Node([System.Text.RegularExpressions.Match]$Node) {
    $bounds = [regex]::Match($Node.Value, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $bounds.Success) { throw "Node has no bounds: $($Node.Value)" }
    $x = [int](([int]$bounds.Groups[1].Value + [int]$bounds.Groups[3].Value) / 2)
    $y = [int](([int]$bounds.Groups[2].Value + [int]$bounds.Groups[4].Value) / 2)
    Adb '-s' $Serial shell input tap $x $y | Out-Null
}

function Clear-FocusedField {
    Adb '-s' $Serial shell input keycombination '-t' 100 `
        KEYCODE_CTRL_LEFT KEYCODE_A | Out-Null
    Adb '-s' $Serial shell input keyevent KEYCODE_DEL | Out-Null
    Start-Sleep -Milliseconds 300
}

function Assert-SellerIme([string]$TargetPackage) {
    $state = (Adb '-s' $Serial shell dumpsys input_method) -join "`n"
    if ($state -notmatch [regex]::Escape("mCurId=$sellerIme") -or
        $state -notmatch 'mInputShown=true' -or
        $state -notmatch 'mWindowVisible=true') {
        throw "SellerKeyboard was not visible for $TargetPackage"
    }
    $windows = (Adb '-s' $Serial shell dumpsys window windows) -join "`n"
    if ($windows -notmatch ('imeInputTarget.*' + [regex]::Escape($TargetPackage))) {
        throw "SellerKeyboard input target was not $TargetPackage"
    }
}

function Wait-ServiceBound([string]$PackageName) {
    $dump = ""
    foreach ($attempt in 1..10) {
        $dump = (Adb '-s' $Serial shell dumpsys accessibility) -join "`n"
        $bound = [regex]::Match($dump, 'Bound services:\{([^\r\n]*)\}').Groups[1].Value
        $enabled = [regex]::Match($dump, 'Enabled services:\{([^\r\n]*)\}').Groups[1].Value
        if (-not [string]::IsNullOrWhiteSpace($bound) -and
            $enabled -match [regex]::Escape($PackageName)) {
            return $dump
        }
        Start-Sleep -Seconds 1
    }
    throw "$PackageName did not become an enabled, bound accessibility service"
}

function Open-TargetEditor {
    Adb '-s' $Serial shell am force-stop $targetPackage | Out-Null
    Adb '-s' $Serial shell am start '-W' '-n' $targetActivity | Out-Null
    Start-Sleep -Seconds 3
    Clear-FocusedField
    Assert-SellerIme $targetPackage
}

function Refocus-TargetEditor([string]$Name) {
    foreach ($attempt in 1..3) {
        # The neutral host requests focus and SHOW_IMPLICIT from onNewIntent,
        # avoiding a touch that could be mistaken for assistive traversal.
        Adb '-s' $Serial shell am start '-W' '-f' '0x20000000' '-n' $targetActivity | Out-Null
        Start-Sleep -Seconds 3
        $state = (Adb '-s' $Serial shell dumpsys input_method) -join "`n"
        if ($state -match [regex]::Escape("mCurId=$sellerIme") -and
            $state -match 'mInputShown=true' -and
            $state -match 'mWindowVisible=true') {
            $windows = (Adb '-s' $Serial shell dumpsys window windows) -join "`n"
            if ($windows -notmatch ('imeInputTarget.*' + [regex]::Escape($targetPackage))) {
                continue
            }
            return
        }
        # Switch Access opens its setup guide on first bind. Close only that
        # guide, then retry the editor's normal focus request.
        Adb '-s' $Serial shell input keyevent KEYCODE_BACK | Out-Null
        Start-Sleep -Seconds 1
    }
    throw "Neutral target editor could not be refocused for $Name"
}

function Verify-ServiceCoexistence(
    [string]$Name,
    [string]$PackageName,
    [string]$ServiceName,
    [bool]$TouchExploration,
    [bool]$RequireShellSource
) {
    Adb '-s' $Serial shell settings put secure enabled_accessibility_services $ServiceName | Out-Null
    Adb '-s' $Serial shell settings put secure accessibility_enabled 1 | Out-Null
    Adb '-s' $Serial shell settings put secure touch_exploration_enabled `
        $(if ($TouchExploration) { 1 } else { 0 }) | Out-Null
    $accessibilityDump = Wait-ServiceBound $PackageName
    Start-Sleep -Seconds 2
    try {
        Assert-SellerIme $targetPackage
    } catch {
        Refocus-TargetEditor $Name
    }

    $xml = Dump-Window "$Name-bound"
    $activeWindow = [regex]::Match(
        $xml,
        '<window[^>]*active="true"[^>]*focused="true"[\s\S]*?</window>'
    ).Value
    if ($activeWindow -notmatch [regex]::Escape("package=`"$targetPackage`"")) {
        Adb '-s' $Serial shell input keyevent KEYCODE_BACK | Out-Null
        Start-Sleep -Seconds 2
        Refocus-TargetEditor "$Name-active-window"
        $xml = Dump-Window "$Name-bound"
        $activeWindow = [regex]::Match(
            $xml,
            '<window[^>]*active="true"[^>]*focused="true"[\s\S]*?</window>'
        ).Value
        if ($activeWindow -notmatch [regex]::Escape("package=`"$targetPackage`"")) {
            throw "$Name did not leave the neutral editor as the active, focused window"
        }
    }
    $sellerSemanticNodes = @(
        [regex]::Matches($xml, '<node[^>]*/?>') | Where-Object {
            $_.Value -match 'package="com\.terasumi\.sellerkeyboard"' -and
            $_.Value -match 'clickable="true"' -and
            $_.Value -match 'content-desc="[^"]+"'
        }
    )
    if ($sellerSemanticNodes.Count -lt 26) {
        throw "$Name exposed only $($sellerSemanticNodes.Count) clickable SellerKeyboard semantic nodes"
    }
    Capture-Screen "$Name-bound"
    $accessibilityDump | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory "$Name-accessibility.txt")

    $shellSourceCoexistence = $false
    if ($RequireShellSource) {
        Adb '-s' $Serial shell input text $canary | Out-Null
        Start-Sleep -Seconds 1
        $resultXml = Dump-Window "$Name-shell-keyboard-source"
        $targetEditor = Find-Node $resultXml 'text' $canary
        $shellSourceCoexistence = $null -ne $targetEditor -and
            $targetEditor.Value -match [regex]::Escape("package=`"$targetPackage`"")
        if (-not $shellSourceCoexistence) {
            throw "$Name did not coexist with the shell virtual keyboard source"
        }
        Assert-SellerIme $targetPackage
        Capture-Screen "$Name-shell-keyboard-source"
        Clear-FocusedField
    }

    [ordered]@{
        servicePackage = $PackageName
        serviceComponent = $ServiceName
        serviceEnabledAndBound = $true
        touchExplorationSettingActive = $TouchExploration
        sellerImeVisible = $true
        sellerClickableSemanticNodeCount = $sellerSemanticNodes.Count
        shellVirtualKeyboardSourceCoexistence = $shellSourceCoexistence
        shellCanary = $canary
    }
}

$devices = @(Adb devices | Where-Object { $_ -match "\tdevice$" })
if ([string]::IsNullOrWhiteSpace($Serial)) {
    if ($devices.Count -ne 1) { throw "Specify -Serial unless exactly one device is connected" }
    $Serial = ($devices[0] -split "\t")[0]
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repositoryRoot `
        "docs\evidence\verification\emulator-accessibility\latest"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$original = [ordered]@{
    defaultIme = Get-Setting secure default_input_method
    showImeWithHardKeyboard = Get-Setting secure show_ime_with_hard_keyboard
    accessibilityEnabled = Get-Setting secure accessibility_enabled
    accessibilityServices = Get-Setting secure enabled_accessibility_services
    touchExplorationEnabled = Get-Setting secure touch_exploration_enabled
    talkBackNotificationPermission = Permission-State $talkBackPackage
    talkBackStopped = Package-Stopped $talkBackPackage
    switchAccessStopped = Package-Stopped $switchAccessPackage
}
$original | ConvertTo-Json | Set-Content -Encoding utf8 `
    (Join-Path $OutputDirectory 'original-state.json')

try {
    foreach ($packageName in @($sellerPackage, $targetPackage, $talkBackPackage, $switchAccessPackage)) {
        if (((Adb '-s' $Serial shell pm path $packageName) -join "") -notmatch '^package:') {
            throw "$packageName is not installed"
        }
    }
    Adb '-s' $Serial shell cmd package unstop $talkBackPackage | Out-Null
    Adb '-s' $Serial shell cmd package unstop $switchAccessPackage | Out-Null
    Adb '-s' $Serial shell pm grant $talkBackPackage $notificationPermission | Out-Null
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Adb '-s' $Serial shell ime enable $sellerIme | Out-Null
    Adb '-s' $Serial shell ime set $sellerIme | Out-Null
    Open-TargetEditor

    $talkBack = Verify-ServiceCoexistence `
        'talkback' $talkBackPackage $talkBackService $true $true

    Adb '-s' $Serial shell settings delete secure enabled_accessibility_services | Out-Null
    Adb '-s' $Serial shell settings put secure accessibility_enabled 0 | Out-Null
    Adb '-s' $Serial shell settings put secure touch_exploration_enabled 0 | Out-Null
    Adb '-s' $Serial shell am force-stop $talkBackPackage | Out-Null
    Start-Sleep -Seconds 2
    Refocus-TargetEditor 'between-services'
    $switchAccess = Verify-ServiceCoexistence `
        'switch-access' $switchAccessPackage $switchAccessService $false $false

    $inputDump = (Adb '-s' $Serial shell dumpsys input) -join "`n"
    $avdKeyboard = [regex]::Match(
        $inputDump,
        'Device \d+: AT Translated Set 2 keyboard[\s\S]*?IsExternal: (true|false)'
    )
    $avdKeyboardExternal = $avdKeyboard.Success -and $avdKeyboard.Groups[1].Value -eq 'true'
    $inputDump | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'input-devices.txt')

    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PARTIAL_PASS'
        serial = $Serial
        api = [int]((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '')
        headlessEmulator = $true
        talkBack = $talkBack
        switchAccess = $switchAccess
        avdKeyboardDetected = $avdKeyboard.Success
        avdKeyboardReportedExternal = $avdKeyboardExternal
        talkBackTraversalAndActionVerified = $false
        switchAccessScanningAndActionVerified = $false
        physicalExternalKeyboardVerified = $false
        claimBoundary = 'Automated API 36 emulator evidence covers enabled/bound TalkBack and Switch Access coexistence in a distinct-UID neutral editor plus exposed SellerKeyboard semantics. TalkBack permits an ADB shell virtual keyboard source; Switch Access intercepts it. The headless emulator cannot prove assistive traversal/action or a physical external keyboard.'
    } | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'summary.json')
} finally {
    Clear-FocusedField
    Adb '-s' $Serial shell am force-stop $targetPackage | Out-Null
    Restore-Setting secure enabled_accessibility_services $original.accessibilityServices
    Restore-Setting secure accessibility_enabled $original.accessibilityEnabled
    Restore-Setting secure touch_exploration_enabled $original.touchExplorationEnabled
    Restore-Setting secure show_ime_with_hard_keyboard $original.showImeWithHardKeyboard
    if ($original.talkBackNotificationPermission -ne 'granted') {
        Adb '-s' $Serial shell pm revoke $talkBackPackage $notificationPermission | Out-Null
    }
    if ($original.talkBackStopped) {
        Adb '-s' $Serial shell am force-stop $talkBackPackage | Out-Null
    }
    if ($original.switchAccessStopped) {
        Adb '-s' $Serial shell am force-stop $switchAccessPackage | Out-Null
    }
    if (-not [string]::IsNullOrWhiteSpace($original.defaultIme) -and
        $original.defaultIme -ne 'null') {
        Adb '-s' $Serial shell ime set $original.defaultIme | Out-Null
    }
}

Write-Host "Emulator accessibility coexistence evidence captured: $OutputDirectory"
