[CmdletBinding()]
param(
    [string]$Serial = '',
    [string]$OutputDirectory = ''
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
} else {
    'adb'
}
$sellerPackage = 'com.terasumi.sellerkeyboard'
$sellerIme = "$sellerPackage/.service.SellerKeyboard"
$hostPackage = 'com.terasumi.sellerkeyboard.benchmarkhost'
$hostActivity = "$hostPackage/.ImeBenchmarkActivity"
$hostBridge = "content://$hostPackage.host-test-bridge"
$beforeCanary = 'skqvz'
$afterCanary = 'mnbvc'

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

function Adb-AllowFailure {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        @(& $adb @Arguments 2>&1)
    } finally {
        $ErrorActionPreference = $previousPreference
    }
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
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hash = $algorithm.ComputeHash($bytes)
        -join ($hash | ForEach-Object { $_.ToString('x2') })
    } finally {
        $algorithm.Dispose()
    }
}

function Dump-Window([string]$Name, [switch]$Screenshot) {
    $remoteXml = "/sdcard/low-memory-$Name.xml"
    $localXml = Join-Path $OutputDirectory "$Name-window.xml"
    Adb '-s' $Serial shell uiautomator dump '--windows' $remoteXml | Out-Null
    Adb '-s' $Serial pull '-q' $remoteXml $localXml | Out-Null
    if ($Screenshot) {
        $remotePng = "/sdcard/low-memory-$Name.png"
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
        throw 'SellerKeyboard is not attached to the recreated neutral host editor'
    }
    [ordered]@{
        selectedIme = $sellerIme
        inputShown = $true
        targetPackage = $hostPackage
    }
}

function Wait-HostSession([int]$Seconds = 20) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try {
            $snapshot = Call-Bridge 'snapshot'
            $xml = Dump-Window 'session-wait'
            if ((Bundle-Value $snapshot 'active') -eq 'true' -and
                $xml -match 'package="com\.terasumi\.sellerkeyboard"' -and
                $xml -match 'content-desc="(?:Shift on|Shift off|Caps Lock on)"') {
                return [ordered]@{
                    snapshot = $snapshot
                    imeSession = Assert-ImeSession
                }
            }
        } catch { }
        Adb '-s' $Serial shell input tap 300 300 | Out-Null
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for neutral-host IME session. Last snapshot: $snapshot"
}

function Type-WithVisibleKeys([string]$Text) {
    foreach ($character in $Text.ToCharArray()) {
        $xml = Dump-Window "key-$character"
        $pattern = '<node[^>]*content-desc="' +
            [regex]::Escape([string]$character) +
            '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*/?>'
        $node = [regex]::Match($xml, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if (-not $node.Success) {
            throw "SellerKeyboard key '$character' was absent from the UI tree"
        }
        $x = [int](([int]$node.Groups[1].Value + [int]$node.Groups[3].Value) / 2)
        $y = [int](([int]$node.Groups[2].Value + [int]$node.Groups[4].Value) / 2)
        Adb '-s' $Serial shell input tap $x $y | Out-Null
        Start-Sleep -Milliseconds 180
    }
}

function Assert-ExactHostText([string]$Expected) {
    $expectedHash = Sha256 $Expected
    $deadline = (Get-Date).AddSeconds(10)
    do {
        $snapshot = Call-Bridge 'snapshot'
        if ((Bundle-Value $snapshot 'text_sha256') -eq $expectedHash -and
            [int](Bundle-Value $snapshot 'text_length') -eq $Expected.Length -and
            [int](Bundle-Value $snapshot 'selection_start') -eq $Expected.Length -and
            [int](Bundle-Value $snapshot 'selection_end') -eq $Expected.Length) {
            return $snapshot
        }
        Start-Sleep -Milliseconds 150
    } while ((Get-Date) -lt $deadline)
    throw "Neutral host text was not exactly '$Expected'. Last snapshot: $snapshot"
}

function Get-HostPid {
    $raw = ((Adb-AllowFailure '-s' $Serial shell pidof $hostPackage) -join '').Trim()
    if ($raw -notmatch '^\d+$') { return $null }
    [int]$raw
}

function Wait-HostProcessGone([int]$PreviousPid, [int]$Seconds = 10) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $pidValue = Get-HostPid
        if ($null -eq $pidValue) { return $true }
        if ($pidValue -ne $PreviousPid) {
            throw "Host PID changed unexpectedly before relaunch: $PreviousPid -> $pidValue"
        }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)
    throw "Android am kill did not remove host PID $PreviousPid"
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
        'docs\evidence\verification\emulator-low-memory\latest'
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
        $path = ((Adb '-s' $Serial shell pm path $packageName) -join '').Trim()
        if ($path -notmatch '^package:') { throw "$packageName is not installed" }
    }
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Adb '-s' $Serial shell ime enable $sellerIme | Out-Null
    Adb '-s' $Serial shell ime set $sellerIme | Out-Null
    Adb '-s' $Serial logcat '-c' | Out-Null
    Adb '-s' $Serial shell am force-stop $hostPackage | Out-Null
    Adb '-s' $Serial shell am start '-W' '-n' $hostActivity | Out-Null
    $initialSession = Wait-HostSession
    $initialSnapshot = Assert-ExactHostText ''

    Type-WithVisibleKeys $beforeCanary
    $beforeSnapshot = Assert-ExactHostText $beforeCanary
    $beforeXml = Dump-Window 'before-process-death' -Screenshot
    if ($beforeXml -notmatch "text=`"$beforeCanary`"") {
        throw 'Pre-death hierarchy does not expose the exact editor canary'
    }
    $beforePid = Get-HostPid
    if ($null -eq $beforePid) { throw 'Could not resolve neutral-host PID before process death' }

    Adb '-s' $Serial shell input keyevent KEYCODE_HOME | Out-Null
    Start-Sleep -Seconds 1
    $trimOutput = Adb '-s' $Serial shell am send-trim-memory '--user' current `
        $hostPackage COMPLETE
    $trimOutput | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'trim-memory-command.txt')
    $killOutput = Adb '-s' $Serial shell am kill '--user' current $hostPackage
    $killOutput | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'am-kill-command.txt')
    $processGone = Wait-HostProcessGone $beforePid

    Adb '-s' $Serial shell am start '-W' '-n' $hostActivity | Out-Null
    $recreatedSession = Wait-HostSession
    $afterPid = Get-HostPid
    if ($null -eq $afterPid -or $afterPid -eq $beforePid) {
        throw "Neutral host did not receive a new process: $beforePid -> $afterPid"
    }
    $resetSnapshot = Assert-ExactHostText ''
    $resetXml = Dump-Window 'after-recreation-empty' -Screenshot
    if ($resetXml -match "text=`"$beforeCanary`"") {
        throw 'Stale pre-death editor text leaked into the recreated host session'
    }

    Type-WithVisibleKeys $afterCanary
    $afterSnapshot = Assert-ExactHostText $afterCanary
    $afterXml = Dump-Window 'after-recreation-typed' -Screenshot
    if ($afterXml -notmatch "text=`"$afterCanary`"") {
        throw 'Recreated host hierarchy does not expose the exact recovery canary'
    }

    $exitInfo = Adb-AllowFailure '-s' $Serial shell dumpsys activity exit-info $hostPackage
    $exitInfo | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'host-process-exit-info.txt')
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
        throw 'SellerKeyboard or the neutral host emitted fatal logs during recreation'
    }

    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        api = [int](((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '').Trim())
        device = ((Adb '-s' $Serial shell getprop ro.product.model) -join '').Trim()
        hostPackage = $hostPackage
        hostVersion = '1.0'
        simulation = [ordered]@{
            trimMemoryLevel = 'COMPLETE'
            backgroundKillCommand = "am kill --user current $hostPackage"
            androidCommandSemantics = 'Kill all background processes associated with the application.'
            processActuallyDied = [bool]$processGone
            pidBefore = $beforePid
            pidAfter = $afterPid
            pidChanged = $beforePid -ne $afterPid
        }
        beforeProcessDeath = [ordered]@{
            exactEditorText = $beforeCanary
            exactEditorTextVerified = $true
            snapshot = $beforeSnapshot
            imeSession = $initialSession.imeSession
        }
        afterProcessRecreation = [ordered]@{
            emptyEditorVerified = $true
            staleTextAbsent = $true
            exactRecoveryText = $afterCanary
            exactRecoveryTextVerified = $true
            snapshotAfterReset = $resetSnapshot
            snapshotAfterTyping = $afterSnapshot
            imeReattachedAndShown = $true
            imeSession = $recreatedSession.imeSession
        }
        sellerOrHostFatalLogCount = 0
        artifacts = [ordered]@{
            beforeScreenshot = 'before-process-death.png'
            beforeHierarchy = 'before-process-death-window.xml'
            recreatedEmptyScreenshot = 'after-recreation-empty.png'
            recreatedEmptyHierarchy = 'after-recreation-empty-window.xml'
            recreatedTypedScreenshot = 'after-recreation-typed.png'
            recreatedTypedHierarchy = 'after-recreation-typed-window.xml'
            processExitInfo = 'host-process-exit-info.txt'
            fatalLogMatches = 'fatal-log-matches.txt'
        }
        claimBoundary = 'Android API 36 emulator low-memory simulation: TRIM_MEMORY_COMPLETE followed by the documented am kill background-process command. The neutral host PID disappears and changes after relaunch; SellerKeyboard reattaches and accepts exact fresh visible-key typing. The host editor owns its own transient draft state, so its empty post-recreation editor is not claimed as IME data loss or preservation.'
    } | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'summary.json')
    Write-Host "Low-memory process recreation passed: $OutputDirectory"
} finally {
    try { Adb '-s' $Serial shell am force-stop $hostPackage | Out-Null } catch { }
    Restore-Setting secure show_ime_with_hard_keyboard $original.showImeWithHardKeyboard
    if (-not [string]::IsNullOrWhiteSpace($original.defaultIme) -and
        $original.defaultIme -ne 'null') {
        try { Adb '-s' $Serial shell ime set $original.defaultIme | Out-Null } catch { }
    }
}
