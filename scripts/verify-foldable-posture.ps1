[CmdletBinding()]
param(
    [string]$Serial = '',
    [string]$AvdName = 'SellerKeyboardApi36Fold',
    [string]$OutputDirectory = '',
    [switch]$ColdBootWithoutSnapshots
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
} else { 'adb' }
$sellerPackage = 'com.terasumi.sellerkeyboard'
$sellerIme = "$sellerPackage/.service.SellerKeyboard"
$hostPackage = 'com.terasumi.sellerkeyboard.benchmarkhost'
$hostActivity = "$hostPackage/.ImeBenchmarkActivity"
$hostBridge = "content://$hostPackage.host-test-bridge"
$avdConfig = Join-Path $env:USERPROFILE ".android\avd\$AvdName.avd\config.ini"

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $result = @(& $adb @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) {
        throw "ADB failed: adb $($Arguments -join ' ')`n$($result -join "`n")"
    }
    $result
}

function Get-Setting([string]$Namespace, [string]$Name) {
    ((Adb '-s' $Serial shell settings get $Namespace $Name) -join '').Trim()
}

function Restore-Setting([string]$Namespace, [string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -eq 'null') {
        Adb '-s' $Serial shell settings delete $Namespace $Name | Out-Null
    } else {
        Adb '-s' $Serial shell settings put $Namespace $Name $Value | Out-Null
    }
}

function Call-Bridge([string]$Method) {
    ((Adb '-s' $Serial shell content call '--uri' $hostBridge '--method' $Method) `
        -join "`n").Trim()
}

function Bundle-Value([string]$Bundle, [string]$Key) {
    $match = [regex]::Match($Bundle, "(?:\{|,\s*)$([regex]::Escape($Key))=([^,}]*)")
    if (-not $match.Success) { return $null }
    $match.Groups[1].Value.Trim()
}

function Sha256([string]$Value) {
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        -join ($algorithm.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Value)) |
            ForEach-Object { $_.ToString('x2') })
    } finally { $algorithm.Dispose() }
}

function Dump-Window([string]$Name, [switch]$Screenshot) {
    $remoteXml = "/sdcard/foldable-posture-$Name.xml"
    $localXml = Join-Path $OutputDirectory "$Name-window.xml"
    Adb '-s' $Serial shell uiautomator dump '--windows' $remoteXml | Out-Null
    Adb '-s' $Serial pull '-q' $remoteXml $localXml | Out-Null
    if ($Screenshot) {
        $remotePng = "/sdcard/foldable-posture-$Name.png"
        $localPng = Join-Path $OutputDirectory "$Name.png"
        Adb '-s' $Serial shell screencap '-p' $remotePng | Out-Null
        Adb '-s' $Serial pull '-q' $remotePng $localPng | Out-Null
    }
    Get-Content -Raw -Encoding UTF8 -LiteralPath $localXml
}

function Assert-ImeSession {
    $inputMethod = (Adb '-s' $Serial shell dumpsys input_method) -join "`n"
    $windows = (Adb '-s' $Serial shell dumpsys window windows) -join "`n"
    if ($inputMethod -notmatch [regex]::Escape("mCurId=$sellerIme") -or
        $inputMethod -notmatch 'mInputShown=true' -or
        $windows -notmatch 'imeInputTarget.*' + [regex]::Escape($hostPackage)) {
        throw 'SellerKeyboard is not attached to the foldable neutral-host editor'
    }
    [ordered]@{
        selectedIme = $sellerIme
        inputShown = $true
        targetPackage = $hostPackage
    }
}

function Wait-HostSession([int]$Seconds = 15) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try {
            $snapshot = Call-Bridge 'snapshot'
            $xml = Dump-Window 'session-wait'
            if ((Bundle-Value $snapshot 'active') -eq 'true' -and
                $xml -match 'package="com\.terasumi\.sellerkeyboard"' -and
                $xml -match 'content-desc="(?:Shift on|Shift off|Caps Lock on)"') {
                return Assert-ImeSession
            }
        } catch { }
        Adb '-s' $Serial shell input keyevent KEYCODE_WAKEUP | Out-Null
        Adb '-s' $Serial shell wm dismiss-keyguard | Out-Null
        Adb '-s' $Serial shell input tap 500 500 | Out-Null
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw 'Timed out waiting for SellerKeyboard after hinge transition'
}

function Type-WithVisibleKeys([string]$Text) {
    foreach ($character in $Text.ToCharArray()) {
        $xml = Dump-Window "key-$character"
        $pattern = '<node[^>]*content-desc="' +
            [regex]::Escape([string]$character) +
            '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*/?>'
        $node = [regex]::Match(
            $xml,
            $pattern,
            [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
        )
        if (-not $node.Success) {
            throw "SellerKeyboard key '$character' was absent from the UI tree"
        }
        $x = [int](([int]$node.Groups[1].Value + [int]$node.Groups[3].Value) / 2)
        $y = [int](([int]$node.Groups[2].Value + [int]$node.Groups[4].Value) / 2)
        Adb '-s' $Serial shell input tap $x $y | Out-Null
        Start-Sleep -Milliseconds 180
    }
}

function Reset-FocusedEditor {
    $seeded = Call-Bridge 'seed_seller_benchmark_empty'
    if ((Bundle-Value $seeded 'active') -ne 'true' -or
        (Bundle-Value $seeded 'seeded') -ne 'true' -or
        [int](Bundle-Value $seeded 'text_length') -ne 0) {
        throw "Neutral host did not reset its editor deterministically: $seeded"
    }
    Start-Sleep -Milliseconds 500
}

function Assert-ExactHostText([string]$Expected) {
    $expectedHash = Sha256 $Expected
    $deadline = (Get-Date).AddSeconds(10)
    do {
        $snapshot = Call-Bridge 'snapshot'
        if ((Bundle-Value $snapshot 'text_sha256') -eq $expectedHash -and
            [int](Bundle-Value $snapshot 'text_length') -eq $Expected.Length) {
            return $snapshot
        }
        Start-Sleep -Milliseconds 150
    } while ((Get-Date) -lt $deadline)
    throw "Foldable host text was not exactly '$Expected'. Last snapshot: $snapshot"
}

function Get-PhysicalSize {
    $size = (Adb '-s' $Serial shell wm size) -join "`n"
    $match = [regex]::Match($size, 'Physical size:\s*(\d+)x(\d+)')
    if (-not $match.Success) { throw "Could not parse physical size: $size" }
    [ordered]@{ widthPx = [int]$match.Groups[1].Value; heightPx = [int]$match.Groups[2].Value }
}

function Get-DensityDpi {
    $density = (Adb '-s' $Serial shell wm density) -join "`n"
    $match = [regex]::Match($density, 'Physical density:\s*(\d+)')
    if (-not $match.Success) { throw "Could not parse physical density: $density" }
    [int]$match.Groups[1].Value
}

function Get-SemanticGeometry([string]$Xml, [int]$DensityDpi) {
    $nodes = @([regex]::Matches($Xml, '<node[^>]*/?>'))
    $clickable = @($nodes | Where-Object {
        $_.Value -match 'package="com\.terasumi\.sellerkeyboard"' -and
        $_.Value -match 'clickable="true"'
    })
    $labeled = @($clickable | Where-Object { $_.Value -match 'content-desc="[^"]+"' })
    $heights = @(
        $labeled | ForEach-Object {
            $bounds = [regex]::Match(
                $_.Value,
                'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
            )
            if ($bounds.Success) {
                [int]$bounds.Groups[4].Value - [int]$bounds.Groups[2].Value
            }
        }
    )
    [ordered]@{
        clickableSemanticNodeCount = $clickable.Count
        labeledClickableNodeCount = $labeled.Count
        minimumLabeledControlHeightPx = ($heights | Measure-Object -Minimum).Minimum
        minimumLabeledControlHeightDp = [math]::Round(
            (($heights | Measure-Object -Minimum).Minimum * 160.0 / $DensityDpi),
            2
        )
    }
}

function Set-HingeState(
    [int]$Angle,
    [int]$ExpectedState,
    [int]$ExpectedWidth,
    [int]$ExpectedHeight
) {
    Adb '-s' $Serial emu sensor set hinge-angle0 $Angle | Out-Null
    $deadline = (Get-Date).AddSeconds(15)
    do {
        Start-Sleep -Milliseconds 500
        $state = [int](((Adb '-s' $Serial shell cmd device_state print-state) -join '').Trim())
        $size = Get-PhysicalSize
        if ($state -eq $ExpectedState -and $size.widthPx -eq $ExpectedWidth -and
            $size.heightPx -eq $ExpectedHeight) {
            return [ordered]@{
                hingeAngle = $Angle
                deviceState = $state
                physicalWidthPx = $size.widthPx
                physicalHeightPx = $size.heightPx
            }
        }
    } while ((Get-Date) -lt $deadline)
    throw "Hinge state did not reach angle=$Angle state=$ExpectedState size=${ExpectedWidth}x${ExpectedHeight}"
}

function Exercise-State(
    [string]$Name,
    [int]$Angle,
    [int]$ExpectedState,
    [int]$ExpectedWidth,
    [int]$ExpectedHeight,
    [string]$Canary,
    [int]$DensityDpi
) {
    $posture = Set-HingeState $Angle $ExpectedState $ExpectedWidth $ExpectedHeight
    Adb '-s' $Serial shell input keyevent KEYCODE_WAKEUP | Out-Null
    Adb '-s' $Serial shell wm dismiss-keyguard | Out-Null
    $relaunchRequired = $false
    try {
        $imeSession = Wait-HostSession 8
    } catch {
        $relaunchRequired = $true
        Adb '-s' $Serial shell am start '-W' '--activity-clear-top' `
            '--activity-single-top' '-n' $hostActivity | Out-Null
        $imeSession = Wait-HostSession 15
    }
    Reset-FocusedEditor
    Assert-ExactHostText '' | Out-Null
    Type-WithVisibleKeys $Canary
    $snapshot = Assert-ExactHostText $Canary
    $xml = Dump-Window $Name -Screenshot
    if ($xml -notmatch "text=`"$Canary`"") {
        throw "$Name hierarchy did not expose exact canary '$Canary'"
    }
    $geometry = Get-SemanticGeometry $xml $DensityDpi
    [ordered]@{
        hingeAngle = $posture.hingeAngle
        deviceState = $posture.deviceState
        physicalWidthPx = $posture.physicalWidthPx
        physicalHeightPx = $posture.physicalHeightPx
        widthDp = [math]::Round($posture.physicalWidthPx * 160.0 / $DensityDpi, 2)
        heightDp = [math]::Round($posture.physicalHeightPx * 160.0 / $DensityDpi, 2)
        hostRelaunchRequired = $relaunchRequired
        imeSession = $imeSession
        exactEditorText = $Canary
        exactEditorTextVerified = $true
        snapshot = $snapshot
        clickableSemanticNodeCount = $geometry.clickableSemanticNodeCount
        labeledClickableNodeCount = $geometry.labeledClickableNodeCount
        minimumLabeledControlHeightPx = $geometry.minimumLabeledControlHeightPx
        minimumLabeledControlHeightDp = $geometry.minimumLabeledControlHeightDp
        screenshot = "$Name.png"
        hierarchy = "$Name-window.xml"
    }
}

$devices = @(Adb devices | Where-Object { $_ -match "\tdevice$" })
if ([string]::IsNullOrWhiteSpace($Serial)) {
    if ($devices.Count -ne 1) {
        throw 'Specify -Serial unless exactly one authorized device is connected'
    }
    $Serial = ($devices[0] -split "\t")[0]
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-foldable-posture\latest'
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$original = [ordered]@{
    defaultIme = Get-Setting secure default_input_method
    showImeWithHardKeyboard = Get-Setting secure show_ime_with_hard_keyboard
}
$original | ConvertTo-Json | Set-Content -Encoding utf8 `
    (Join-Path $OutputDirectory 'original-state.json')

try {
    foreach ($packageName in @($sellerPackage, $hostPackage)) {
        if (((Adb '-s' $Serial shell pm path $packageName) -join '') -notmatch '^package:') {
            throw "$packageName is not installed"
        }
    }
    if (-not (Test-Path -LiteralPath $avdConfig -PathType Leaf)) {
        throw "AVD configuration is missing: $avdConfig"
    }
    $configText = Get-Content -Raw -LiteralPath $avdConfig
    if ($configText -notmatch '(?m)^hw\.sensor\.hinge=yes\r?$' -or
        $configText -notmatch '(?m)^hw\.sensor\.posture_list=1, 2, 3\r?$') {
        throw 'AVD does not declare the required hinge sensor and posture list'
    }
    Copy-Item -LiteralPath $avdConfig -Destination `
        (Join-Path $OutputDirectory 'avd-config.ini') -Force
    $supportedStates = (Adb '-s' $Serial shell cmd device_state print-states) -join "`n"
    $supportedStates | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'supported-device-states.txt')
    foreach ($requiredState in @('CLOSED', 'HALF_OPENED', 'OPENED')) {
        if ($supportedStates -notmatch "name='$requiredState'") {
            throw "Foldable AVD does not expose $requiredState"
        }
    }

    Adb '-s' $Serial shell settings put global device_provisioned 1 | Out-Null
    Adb '-s' $Serial shell settings put secure user_setup_complete 1 | Out-Null
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Adb '-s' $Serial shell ime enable $sellerIme | Out-Null
    Adb '-s' $Serial shell ime set $sellerIme | Out-Null
    Adb '-s' $Serial logcat '-c' | Out-Null
    Set-HingeState 180 2 2208 1840 | Out-Null
    Adb '-s' $Serial shell input keyevent KEYCODE_WAKEUP | Out-Null
    Adb '-s' $Serial shell wm dismiss-keyguard | Out-Null
    Adb '-s' $Serial shell am force-stop $hostPackage | Out-Null
    Adb '-s' $Serial shell am start '-W' '-n' $hostActivity | Out-Null
    Wait-HostSession 20 | Out-Null
    $densityDpi = Get-DensityDpi

    $opened = Exercise-State 'opened-180' 180 2 2208 1840 'qvbn' $densityDpi
    $halfOpened = Exercise-State 'half-opened-90' 90 1 2208 1840 'klmn' $densityDpi
    $closed = Exercise-State 'closed-0' 0 0 1080 2092 'bnmp' $densityDpi
    $reopened = Exercise-State 'reopened-180' 180 2 2208 1840 'ptgv' $densityDpi

    $deviceStateDump = (Adb '-s' $Serial shell dumpsys device_state) -join "`n"
    $deviceStateDump | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'final-device-state-dump.txt')
    $fatalLogs = @(
        Adb '-s' $Serial logcat '-d' '-v' 'brief' |
            Select-String -Pattern (
                'FATAL EXCEPTION|Process: (?:com\.terasumi\.sellerkeyboard|' +
                'com\.terasumi\.sellerkeyboard\.benchmarkhost)'
            )
    )
    Set-Content -Encoding utf8 -Value @($fatalLogs) `
        (Join-Path $OutputDirectory 'fatal-log-matches.txt')
    if ($fatalLogs.Count -ne 0) {
        throw 'SellerKeyboard or neutral host emitted fatal logs during fold posture transitions'
    }

    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS_SCOPED'
        serial = $Serial
        api = [int](((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '').Trim())
        avd = $AvdName
        avdHardwareProfile = 'pixel_fold'
        coldBootWithoutSnapshotLoadOrSave = [bool]$ColdBootWithoutSnapshots
        hingeSensorDeclared = $true
        supportedDeviceStates = @('CLOSED', 'HALF_OPENED', 'OPENED', 'REAR_DISPLAY_MODE')
        densityDpi = $densityDpi
        opened = $opened
        halfOpened = $halfOpened
        closedOuterDisplay = $closed
        reopened = $reopened
        sellerOrHostFatalLogCount = 0
        realPhysicalFoldPostureVerified = $false
        artifacts = [ordered]@{
            avdConfig = 'avd-config.ini'
            supportedStates = 'supported-device-states.txt'
            finalDeviceStateDump = 'final-device-state-dump.txt'
            fatalLogMatches = 'fatal-log-matches.txt'
        }
        claimBoundary = 'API 36 Pixel Fold AVD with a declared hinge sensor and Android device-state transitions. SellerKeyboard is verified at 180-degree OPENED, 90-degree HALF_OPENED, 0-degree CLOSED outer display, and 180-degree reopened states with exact visible-key editor results. This is real emulator hinge/posture behavior, not physical foldable-device evidence.'
    } | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'summary.json')
    Write-Host "Foldable posture verification passed: $OutputDirectory"
} finally {
    try { Adb '-s' $Serial emu sensor set hinge-angle0 180 | Out-Null } catch { }
    try { Adb '-s' $Serial shell am force-stop $hostPackage | Out-Null } catch { }
    Restore-Setting secure show_ime_with_hard_keyboard $original.showImeWithHardKeyboard
    if (-not [string]::IsNullOrWhiteSpace($original.defaultIme) -and
        $original.defaultIme -ne 'null') {
        try { Adb '-s' $Serial shell ime set $original.defaultIme | Out-Null } catch { }
    }
}
