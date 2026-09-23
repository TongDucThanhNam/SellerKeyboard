[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateSet('Start', 'Finish')][string]$Phase,
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$PackageName,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string[]]$Canary = @(),
    [ValidateSet('Zero', 'Record')][string]$NetworkExpectation = 'Zero'
)

$ErrorActionPreference = 'Stop'
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' } else { 'adb' }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: $($Arguments -join ' ')`n$($result -join "`n")"
    }
    $result
}

function Invoke-AdbCaptureWithTimeout {
    param(
        [string[]]$Arguments,
        [string]$Name,
        [int]$TimeoutMilliseconds = 7000
    )
    $stdout = Join-Path $OutputDirectory "$Name-stdout.txt"
    $stderr = Join-Path $OutputDirectory "$Name-stderr.txt"
    Remove-Item -LiteralPath $stdout, $stderr -Force -ErrorAction SilentlyContinue
    $process = Start-Process -FilePath $adb -ArgumentList $Arguments `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
        -WindowStyle Hidden -PassThru
    $completed = $process.WaitForExit($TimeoutMilliseconds)
    if (-not $completed) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        $process.WaitForExit()
    }
    $out = if (Test-Path -LiteralPath $stdout) {
        Get-Content -Raw -LiteralPath $stdout
    } else { '' }
    $err = if (Test-Path -LiteralPath $stderr) {
        Get-Content -Raw -LiteralPath $stderr
    } else { '' }
    [ordered]@{
        completed = $completed
        exitCode = if ($completed) { $process.ExitCode } else { $null }
        output = "$out`n$err".Trim()
    }
}

function Capture-CompletedSuccessfully($Capture) {
    # Windows PowerShell 5.1 leaves Process.ExitCode unset when a redirected
    # Start-Process is observed through WaitForExit(timeout), even for exit 0.
    # PowerShell 7 returns the numeric code. Preserve known nonzero failures
    # while accepting a completed legacy-host capture whose output is then
    # validated by the caller.
    $Capture.completed -and
        ($null -eq $Capture.exitCode -or [int]$Capture.exitCode -eq 0)
}

function Resolve-PackageUid {
    $uidOutput = (Adb '-s' $Serial shell pm list packages '-U' $PackageName) -join "`n"
    $uidMatch = [regex]::Match(
        $uidOutput,
        "(?m)^package:$([regex]::Escape($PackageName))\s+uid:(\d+)\s*$"
    )
    if (-not $uidMatch.Success) {
        throw "Could not resolve exact UID for ${PackageName}: $uidOutput"
    }
    $uid = [int]$uidMatch.Groups[1].Value
    if ($uid -lt 10000) { throw "Resolved invalid application UID $uid for $PackageName" }
    $uid
}

function Get-UidStats([int]$Uid, [string]$Prefix) {
    $poll = Invoke-AdbCaptureWithTimeout -Name "$Prefix-netstats-poll" -Arguments @(
        '-s', $Serial, 'shell', 'dumpsys', 'netstats', '--poll'
    )
    if (-not (Capture-CompletedSuccessfully $poll) -or $poll.output -match 'DUMP TIMEOUT') {
        return [ordered]@{
            supported = $false
            reason = 'Android netstats poll unavailable or timed out.'
            matchingLines = 0
            rxBytes = $null
            txBytes = $null
            lines = @($poll.output)
        }
    }
    $detail = Invoke-AdbCaptureWithTimeout -Name "$Prefix-netstats-detail" -Arguments @(
        '-s', $Serial, 'shell', 'dumpsys', 'netstats', 'detail'
    )
    if (-not (Capture-CompletedSuccessfully $detail) -or $detail.output -match 'DUMP TIMEOUT') {
        return [ordered]@{
            supported = $false
            reason = 'Android netstats detail unavailable or timed out.'
            matchingLines = 0
            rxBytes = $null
            txBytes = $null
            lines = @($detail.output)
        }
    }
    $lines = @($detail.output -split "`n" | Where-Object { $_ -match "uid=$Uid(?:\D|$)" })
    $rx = 0L
    $tx = 0L
    foreach ($line in $lines) {
        foreach ($match in [regex]::Matches($line, 'rxBytes=(\d+)')) {
            $rx += [long]$match.Groups[1].Value
        }
        foreach ($match in [regex]::Matches($line, 'txBytes=(\d+)')) {
            $tx += [long]$match.Groups[1].Value
        }
    }
    [ordered]@{
        supported = $true
        reason = ''
        matchingLines = $lines.Count
        rxBytes = $rx
        txBytes = $tx
        lines = $lines
    }
}

function Get-UidScopedLogs([int]$Uid) {
    $direct = Invoke-AdbCaptureWithTimeout -Name 'finish-uid-logcat-direct' `
        -TimeoutMilliseconds 30000 `
        -Arguments @('-s', $Serial, 'logcat', '-d', '-v', 'threadtime', "--uid=$Uid")
    if ((Capture-CompletedSuccessfully $direct) -and
        $direct.output -notmatch 'Unrecognized Option') {
        return $direct.output
    }

    # A short-lived provider can exit before Finish while its UID-scoped log
    # records remain queryable. Retry through the device-side logcat command
    # before using the legacy live-PID fallback required by older Android.
    $deviceDirect = Invoke-AdbCaptureWithTimeout -Name 'finish-uid-logcat-device' `
        -TimeoutMilliseconds 30000 `
        -Arguments @(
            '-s', $Serial, 'shell', 'logcat', '-d', '-v', 'threadtime', "--uid=$Uid"
        )
    if ((Capture-CompletedSuccessfully $deviceDirect) -and
        $deviceDirect.output -notmatch 'Unrecognized Option') {
        return $deviceDirect.output
    }

    $verifiedPids = @()
    $psLines = @(Adb '-s' $Serial shell ps '-A')
    foreach ($line in $psLines) {
        $parts = @($line.Trim() -split '\s+')
        if ($parts.Count -lt 2) { continue }
        $processName = $parts[-1]
        if ($processName -ne $PackageName -and
            -not $processName.StartsWith("${PackageName}:")) {
            continue
        }
        $pidValue = 0
        if (-not [int]::TryParse($parts[1], [ref]$pidValue)) { continue }
        $status = (Adb '-s' $Serial shell cat "/proc/$pidValue/status") -join "`n"
        if ($status -match "(?m)^Uid:\s+$Uid(?:\s|$)") { $verifiedPids += $pidValue }
    }
    $verifiedPids = @($verifiedPids | Sort-Object -Unique)
    if ($verifiedPids.Count -eq 0) {
        throw "Could not resolve a live process for exact package UID $Uid"
    }
    $fullLogs = (Adb '-s' $Serial logcat '-d' '-v' 'threadtime') -join "`n"
    $pidSet = @{}
    foreach ($pidValue in $verifiedPids) { $pidSet[[string]$pidValue] = $true }
    $filtered = @($fullLogs -split "`n" | Where-Object {
        $match = [regex]::Match(
            $_,
            '^\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d+\s+(\d+)\s+(\d+)\s'
        )
        $match.Success -and $pidSet.ContainsKey($match.Groups[1].Value)
    })
    (@("# Fallback: verified exact UID $Uid for PIDs $($verifiedPids -join ',')") +
        $filtered) -join "`n"
}

$uid = Resolve-PackageUid
$baselinePath = Join-Path $OutputDirectory 'baseline.json'
if ($Phase -eq 'Start') {
    $clear = Invoke-AdbCaptureWithTimeout -Name 'start-logcat-clear' -Arguments @(
        '-s', $Serial, 'logcat', '-c'
    )
    $before = Get-UidStats $uid 'start'
    [ordered]@{
        capturedAt = (Get-Date).ToString('o')
        serial = $Serial
        packageName = $PackageName
        uid = $uid
        networkExpectation = $NetworkExpectation
        logcatClearSucceeded = Capture-CompletedSuccessfully $clear
        network = $before
    } | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 $baselinePath
    Write-Host "UID attribution baseline captured for $PackageName ($uid)"
    return
}

if (-not (Test-Path -LiteralPath $baselinePath)) {
    throw "UID attribution baseline is missing: $baselinePath"
}
$baseline = Get-Content -Raw -LiteralPath $baselinePath | ConvertFrom-Json
if ([int]$baseline.uid -ne $uid -or $baseline.packageName -ne $PackageName -or
    $baseline.serial -ne $Serial -or $baseline.networkExpectation -ne $NetworkExpectation) {
    throw 'UID attribution baseline does not match the finish request.'
}
$after = Get-UidStats $uid 'finish'
$logs = Get-UidScopedLogs $uid
$logs | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'uid-logcat.txt')
$occurrences = 0
$canaryResults = @()
foreach ($value in $Canary) {
    $count = ([regex]::Matches($logs, [regex]::Escape($value))).Count
    $occurrences += $count
    $canaryResults += [ordered]@{ value = $value; occurrences = $count }
}
$networkSupported = [bool]$baseline.network.supported -and [bool]$after.supported
$rxDelta = if ($networkSupported) {
    [long]$after.rxBytes - [long]$baseline.network.rxBytes
} else { $null }
$txDelta = if ($networkSupported) {
    [long]$after.txBytes - [long]$baseline.network.txBytes
} else { $null }
@($baseline.network.lines + $after.lines) | Set-Content -Encoding utf8 `
    (Join-Path $OutputDirectory 'uid-netstats-lines.txt')
$summary = [ordered]@{
    capturedAt = (Get-Date).ToString('o')
    serial = $Serial
    packageName = $PackageName
    uid = $uid
    networkExpectation = $NetworkExpectation
    networkAccountingSupported = $networkSupported
    networkAccountingReason = if ($networkSupported) { '' } else {
        @($baseline.network.reason, $after.reason) -join ' '
    }
    beforeRxBytes = $baseline.network.rxBytes
    afterRxBytes = $after.rxBytes
    beforeTxBytes = $baseline.network.txBytes
    afterTxBytes = $after.txBytes
    rxDelta = $rxDelta
    txDelta = $txDelta
    canaries = $canaryResults
    canaryOccurrencesInUidLog = $occurrences
}
$summary | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 `
    (Join-Path $OutputDirectory 'summary.json')
if ($NetworkExpectation -eq 'Zero' -and $networkSupported -and
    ($rxDelta -ne 0 -or $txDelta -ne 0)) {
    throw "UID network bytes changed during attributed feature flow: RX=$rxDelta TX=$txDelta"
}
if ($occurrences -ne 0) {
    throw "Controlled feature text appeared $occurrences time(s) in exact-UID logs."
}
Write-Host "UID attribution passed for $PackageName ($uid)"
