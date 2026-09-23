[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' } else { 'adb' }
$package = 'com.terasumi.sellerkeyboard'
$hostPackage = 'com.terasumi.sellerkeyboard.benchmarkhost'
$ime = "$package/.service.SellerKeyboard"
$activity = "$hostPackage/.ImeBenchmarkActivity"
$hostBridge = "content://$hostPackage.host-test-bridge"
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: $($Arguments -join ' ')`n$($result -join "`n")"
    }
    $result
}

function Call-Bridge([string]$Uri, [string]$Method, [string]$Argument = '') {
    $arguments = @('-s', $Serial, 'shell', 'content', 'call', '--uri', $Uri, '--method', $Method)
    if (-not [string]::IsNullOrWhiteSpace($Argument)) {
        $arguments += @('--arg', $Argument)
    }
    ((Adb @arguments) -join "`n").Trim()
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

function Capture-Window([string]$Name, [switch]$Screenshot) {
    $remoteXml = "/sdcard/core-interactions-$Name.xml"
    $localXml = Join-Path $OutputDirectory "$Name-window.xml"
    Adb '-s' $Serial shell uiautomator dump '--windows' $remoteXml | Out-Null
    Adb '-s' $Serial pull '-q' $remoteXml $localXml | Out-Null
    if ($Screenshot) {
        $remotePng = "/sdcard/core-interactions-$Name.png"
        $localPng = Join-Path $OutputDirectory "$Name.png"
        Adb '-s' $Serial shell screencap '-p' $remotePng | Out-Null
        Adb '-s' $Serial pull '-q' $remotePng $localPng | Out-Null
    }
    $localXml
}

function Find-DescriptionNode([string]$Xml, [string]$DescriptionPattern) {
    $matches = [regex]::Matches(
        $Xml,
        '<node[^>]*content-desc="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*/?>'
    )
    foreach ($match in $matches) {
        if ($match.Groups[1].Value -match $DescriptionPattern) { return $match }
    }
    $null
}

function Find-ValueNode([string]$Xml, [string]$Value) {
    $escaped = [regex]::Escape($Value)
    [regex]::Match(
        $Xml,
        '<node[^>]*(?:text|content-desc|hint)="' + $escaped +
            '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*/?>'
    )
}

function Await-Description(
    [string]$DescriptionPattern,
    [string]$Name,
    [int]$Seconds = 15
) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $path = Capture-Window $Name
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $path
        $node = Find-DescriptionNode $xml $DescriptionPattern
        if ($null -ne $node) {
            return [pscustomobject]@{ Node = $node; Path = $path; Xml = $xml }
        }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for content description /$DescriptionPattern/ in $path"
}

function Node-Center([System.Text.RegularExpressions.Match]$Node) {
    [pscustomobject]@{
        X = [int](([int]$Node.Groups[2].Value + [int]$Node.Groups[4].Value) / 2)
        Y = [int](([int]$Node.Groups[3].Value + [int]$Node.Groups[5].Value) / 2)
        Left = [int]$Node.Groups[2].Value
        Top = [int]$Node.Groups[3].Value
        Right = [int]$Node.Groups[4].Value
        Bottom = [int]$Node.Groups[5].Value
    }
}

function Tap-Description([string]$DescriptionPattern, [string]$Name) {
    $found = Await-Description $DescriptionPattern $Name
    $point = Node-Center $found.Node
    Adb '-s' $Serial shell input tap $point.X $point.Y | Out-Null
    Start-Sleep -Milliseconds 250
}

function Tap-Twice([string]$DescriptionPattern, [string]$Name) {
    $found = Await-Description $DescriptionPattern $Name
    $point = Node-Center $found.Node
    Adb '-s' $Serial shell input tap $point.X $point.Y | Out-Null
    Start-Sleep -Milliseconds 100
    Adb '-s' $Serial shell input tap $point.X $point.Y | Out-Null
    Start-Sleep -Milliseconds 300
}

function Seed-Fixture([string]$Name) {
    $seeded = Call-Bridge $hostBridge 'seed_core_fixture' $Name
    if ((Bundle-Value $seeded 'seeded') -ne 'true' -or
        (Bundle-Value $seeded 'fixture') -ne $Name) {
        throw "Neutral host did not seed core fixture '$Name': $seeded"
    }
    Start-Sleep -Milliseconds 500
    $seeded
}

function Wait-HostState(
    [string]$ExpectedHash,
    [int]$ExpectedSelectionStart,
    [int]$ExpectedSelectionEnd,
    [string]$Name,
    [int]$Seconds = 10
) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $snapshot = Call-Bridge $hostBridge 'snapshot'
        if ((Bundle-Value $snapshot 'text_sha256') -eq $ExpectedHash -and
            [int](Bundle-Value $snapshot 'selection_start') -eq $ExpectedSelectionStart -and
            [int](Bundle-Value $snapshot 'selection_end') -eq $ExpectedSelectionEnd) {
            return $snapshot
        }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)
    throw "Host state did not reach '$Name'. Last: $snapshot"
}

function Wait-HostLengthLessThan([int]$MaximumExclusive, [string]$Name) {
    $deadline = (Get-Date).AddSeconds(10)
    do {
        $snapshot = Call-Bridge $hostBridge 'snapshot'
        $length = [int](Bundle-Value $snapshot 'text_length')
        if ($length -lt $MaximumExclusive) { return $snapshot }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)
    throw "Host length did not fall below $MaximumExclusive during '$Name'. Last: $snapshot"
}

function Start-HostImeSession([int]$Seconds = 45) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $path = Capture-Window 'host-startup'
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $path
        if ($xml -match "System UI isn.t responding") {
            $wait = Find-ValueNode $xml 'Wait'
            if ($wait.Success) {
                $x = [int](([int]$wait.Groups[1].Value + [int]$wait.Groups[3].Value) / 2)
                $y = [int](([int]$wait.Groups[2].Value + [int]$wait.Groups[4].Value) / 2)
                Adb '-s' $Serial shell input tap $x $y | Out-Null
                Start-Sleep -Seconds 2
                continue
            }
        }
        $snapshot = Call-Bridge $hostBridge 'snapshot'
        $shift = Find-DescriptionNode $xml '^(?:Shift on|Shift off|Caps Lock on)$'
        if ((Bundle-Value $snapshot 'active') -eq 'true' -and $null -ne $shift) {
            return $snapshot
        }
        Adb '-s' $Serial shell ime enable $ime | Out-Null
        Adb '-s' $Serial shell ime set $ime | Out-Null
        Adb '-s' $Serial shell input tap 300 300 | Out-Null
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Timed out activating SellerKeyboard in the neutral host. Last: $snapshot"
}

$originalShowImeWithHardKeyboard = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
try {
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    $api = [int](((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '').Trim())
    if ($api -ge 30) { Adb '-s' $Serial shell ime reset | Out-Null }
    Adb '-s' $Serial shell ime enable $ime | Out-Null
    Adb '-s' $Serial shell ime set $ime | Out-Null
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $activity | Out-Null
    Start-HostImeSession | Out-Null

    Seed-Fixture 'empty_sentence' | Out-Null
    Await-Description '^Shift on$' 'initial-auto-shift' | Out-Null
    Tap-Description '^(?:B|b)$' 'one-shot-b'
    Wait-HostState (Sha256 'B') 1 1 'initial one-shot Shift commit' | Out-Null
    Await-Description '^Shift off$' 'one-shot-consumed' | Out-Null

    Tap-Description '^Shift off$' 'single-shift'
    Await-Description '^Shift on$' 'single-shift-on' | Out-Null
    Tap-Description '^(?:C|c)$' 'one-shot-c'
    Wait-HostState (Sha256 'BC') 2 2 'manual one-shot Shift commit' | Out-Null
    Await-Description '^Shift off$' 'manual-one-shot-consumed' | Out-Null

    Tap-Twice '^Shift off$' 'caps-lock-double-tap'
    Await-Description '^Caps Lock on$' 'caps-lock-on' | Out-Null
    Tap-Description '^(?:D|d)$' 'caps-d'
    Tap-Description '^(?:F|f)$' 'caps-f'
    Wait-HostState (Sha256 'BCDF') 4 4 'Caps Lock commits' | Out-Null
    Await-Description '^Caps Lock on$' 'caps-persists' | Out-Null
    Capture-Window 'caps-lock-proof' -Screenshot | Out-Null
    Tap-Description '^Caps Lock on$' 'caps-lock-off'
    Await-Description '^Shift off$' 'caps-exited' | Out-Null
    Tap-Description '^(?:B|b)$' 'lowercase-b'
    Wait-HostState (Sha256 'BCDFb') 5 5 'Caps Lock exit commit' | Out-Null

    Seed-Fixture 'punctuation_sentence' | Out-Null
    Await-Description '^Shift on$' 'punctuation-auto-shift' | Out-Null
    Tap-Description '^(?:B|b)$' 'punctuation-b'
    Wait-HostState (Sha256 'done. B') 7 7 'punctuation auto-cap commit' | Out-Null

    Seed-Fixture 'cursor_middle' | Out-Null
    $space = Await-Description '^Space(?:\.|$)' 'space-cursor-move'
    $spaceBounds = Node-Center $space.Node
    $spaceMoveEndX = [Math]::Min($spaceBounds.Right - 12, $spaceBounds.X + 120)
    Adb '-s' $Serial shell input swipe $spaceBounds.X $spaceBounds.Y `
        $spaceMoveEndX $spaceBounds.Y 250 | Out-Null
    Start-Sleep -Milliseconds 300
    $cursorMoved = Call-Bridge $hostBridge 'snapshot'
    $movedSelection = [int](Bundle-Value $cursorMoved 'selection_end')
    if ((Bundle-Value $cursorMoved 'text_sha256') -ne (Sha256 'abcdef') -or
        $movedSelection -le 3 -or
        [int](Bundle-Value $cursorMoved 'selection_start') -ne $movedSelection) {
        throw "Space cursor gesture did not move right without editing: $cursorMoved"
    }

    Seed-Fixture 'cursor_middle' | Out-Null
    $space = Await-Description '^Space(?:\.|$)' 'space-pointer-exit'
    $spaceBounds = Node-Center $space.Node
    $spaceOutsideX = [Math]::Min(1070, $spaceBounds.Right + 120)
    Adb '-s' $Serial shell input swipe $spaceBounds.X $spaceBounds.Y `
        $spaceOutsideX $spaceBounds.Y 350 | Out-Null
    Wait-HostState (Sha256 'abcdef') 3 3 'space pointer-exit restore' | Out-Null

    Seed-Fixture 'cursor_end' | Out-Null
    $delete = Await-Description '^Delete$' 'swipe-delete'
    $deleteBounds = Node-Center $delete.Node
    $deleteStartX = $deleteBounds.Right - 12
    $deleteEndX = $deleteBounds.Left + 12
    Adb '-s' $Serial shell input swipe $deleteStartX $deleteBounds.Y `
        $deleteEndX $deleteBounds.Y 250 | Out-Null
    $swipeDeleted = Wait-HostLengthLessThan 6 'swipe delete commit'
    if ([int](Bundle-Value $swipeDeleted 'text_length') -le 0) {
        throw "Swipe delete removed the entire controlled fixture unexpectedly: $swipeDeleted"
    }

    Seed-Fixture 'cursor_end' | Out-Null
    $delete = Await-Description '^Delete$' 'delete-pointer-exit'
    $deleteBounds = Node-Center $delete.Node
    $deleteStartX = $deleteBounds.Right - 12
    $deleteOutsideX = [Math]::Max(0, $deleteBounds.Left - 140)
    Adb '-s' $Serial shell input swipe $deleteStartX $deleteBounds.Y `
        $deleteOutsideX $deleteBounds.Y 350 | Out-Null
    Wait-HostState (Sha256 'abcdef') 6 6 'delete pointer-exit restore' | Out-Null

    Seed-Fixture 'delete_repeat' | Out-Null
    $delete = Await-Description '^Delete$' 'hold-delete-repeat'
    $deleteBounds = Node-Center $delete.Node
    Adb '-s' $Serial shell input swipe $deleteBounds.X $deleteBounds.Y `
        $deleteBounds.X $deleteBounds.Y 900 | Out-Null
    $repeatSnapshot = Wait-HostLengthLessThan 20 'hold delete repeat'
    $repeatLength = [int](Bundle-Value $repeatSnapshot 'text_length')
    $repeatHash = Bundle-Value $repeatSnapshot 'text_sha256'
    Start-Sleep -Milliseconds 500
    $afterRelease = Call-Bridge $hostBridge 'snapshot'
    if ([int](Bundle-Value $afterRelease 'text_length') -ne $repeatLength -or
        (Bundle-Value $afterRelease 'text_sha256') -ne $repeatHash) {
        throw "Hold-delete continued after pointer release: before=$repeatSnapshot after=$afterRelease"
    }
    Capture-Window 'gesture-final' -Screenshot | Out-Null

    [ordered]@{
        overall = 'PASS'
        serial = $Serial
        api = $api
        targetPackage = $hostPackage
        targetIsDistinctPackage = $hostPackage -ne $package
        initialAutoCapsVerified = $true
        oneShotShiftVerified = $true
        capsLockDoubleTapVerified = $true
        capsLockPersistenceAndExitVerified = $true
        punctuationAutoCapsVerified = $true
        spaceCursorMoveVerified = $true
        spacePointerExitRestoreVerified = $true
        swipeDeleteVerified = $true
        swipeDeletePointerExitRestoreVerified = $true
        holdDeleteRepeatVerified = $true
        holdDeleteStopsOnReleaseVerified = $true
        multiTouchCancellationVerified = $false
        hashOnlyHostBridge = $true
    } | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'summary.json')
    Write-Host "Connected core Shift and gesture interactions passed: $OutputDirectory"
} finally {
    if ($originalShowImeWithHardKeyboard -match '^\d+$') {
        Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard `
            $originalShowImeWithHardKeyboard | Out-Null
    } else {
        Adb '-s' $Serial shell settings delete secure show_ime_with_hard_keyboard | Out-Null
    }
}
