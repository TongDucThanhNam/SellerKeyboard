[CmdletBinding()]
param(
    [string]$Serial = "",
    [string]$OutputDirectory = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" } else { "adb" }
$package = "com.terasumi.sellerkeyboard"
$sellerIme = "$package/.service.SellerKeyboard"
$gboardIme = "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
$activity = "$package/.benchmark.ImeBenchmarkActivity"
$probeCases = @(
    [pscustomobject]@{
        id = 'plain'
        inputType = 1
        imeOptions = 6
        hint = 'PrivacyPlainEditor'
        privateImeOptions = ''
        canary = 'sellerprobeplain'
        expectVisibleText = $true
        expectPasswordNode = $false
    }
    [pscustomobject]@{
        id = 'password'
        inputType = 129
        imeOptions = 6
        hint = 'PrivacyPasswordEditor'
        privateImeOptions = ''
        canary = 'sellerprobepassword'
        expectVisibleText = $false
        expectPasswordNode = $true
    }
    [pscustomobject]@{
        id = 'otp-authentication'
        inputType = 2
        imeOptions = 6
        hint = 'OneTimeOtpCode'
        privateImeOptions = 'sellerkeyboard.authentication'
        canary = '731905284617'
        expectVisibleText = $true
        expectPasswordNode = $false
    }
)

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments -join ' ')`n$($result -join "`n")" }
    $result
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $devices = @(Adb devices | Where-Object { $_ -match "\tdevice$" })
    if ($devices.Count -ne 1) { throw "Specify -Serial unless exactly one device is connected." }
    $Serial = ($devices[0] -split "\t")[0]
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $root "docs\evidence\device-2026-07-11-privacy"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$originalIme = ((Adb '-s' $Serial shell settings get secure default_input_method) -join '').Trim()
$uidOutput = (Adb '-s' $Serial shell pm list packages '-U' $package) -join "`n"
$uidMatch = [regex]::Match(
    $uidOutput,
    "(?m)^package:$([regex]::Escape($package))\s+uid:(\d+)\s*$"
)
if (-not $uidMatch.Success) {
    throw "Could not resolve the exact app UID for $package. Output:`n$uidOutput"
}
$uid = [int]$uidMatch.Groups[1].Value
if ($uid -lt 10000) {
    throw "Resolved invalid application UID $uid for $package."
}
Write-Host "Privacy probe UID resolved: $uid"

function Invoke-AdbCaptureWithTimeout {
    param(
        [string[]]$Arguments,
        [string]$Name,
        [int]$TimeoutMilliseconds = 5000
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
    # Windows PowerShell 5.1 does not populate ExitCode after the timeout
    # overload of WaitForExit for this redirected Start-Process path.
    $Capture.completed -and
        ($null -eq $Capture.exitCode -or [int]$Capture.exitCode -eq 0)
}

$networkAccountingSupported = $true
$networkAccountingReason = ''

function Get-UidStats {
    if (-not $networkAccountingSupported) {
        return [ordered]@{ matchingLines = 0; rxBytes = $null; txBytes = $null; lines = @($networkAccountingReason) }
    }
    $poll = Invoke-AdbCaptureWithTimeout -Name 'netstats-poll' -Arguments @(
        '-s', $Serial, 'shell', 'dumpsys', 'netstats', '--poll'
    )
    if (-not (Capture-CompletedSuccessfully $poll) -or
        $poll.output -match 'DUMP TIMEOUT') {
        $script:networkAccountingSupported = $false
        $script:networkAccountingReason = 'Android netstats poll unavailable or timed out.'
        return [ordered]@{ matchingLines = 0; rxBytes = $null; txBytes = $null; lines = @($networkAccountingReason, $poll.output) }
    }
    $detail = Invoke-AdbCaptureWithTimeout -Name 'netstats-detail' -Arguments @(
        '-s', $Serial, 'shell', 'dumpsys', 'netstats', 'detail'
    )
    if (-not (Capture-CompletedSuccessfully $detail) -or
        $detail.output -match 'DUMP TIMEOUT') {
        $script:networkAccountingSupported = $false
        $script:networkAccountingReason = 'Android netstats detail unavailable or timed out.'
        return [ordered]@{ matchingLines = 0; rxBytes = $null; txBytes = $null; lines = @($networkAccountingReason, $detail.output) }
    }
    $lines = @($detail.output -split "`n" | Where-Object { $_ -match "uid=$uid(?:\D|$)" })
    $rx = 0L; $tx = 0L
    foreach ($line in $lines) {
        foreach ($match in [regex]::Matches($line, 'rxBytes=(\d+)')) { $rx += [long]$match.Groups[1].Value }
        foreach ($match in [regex]::Matches($line, 'txBytes=(\d+)')) { $tx += [long]$match.Groups[1].Value }
    }
    [ordered]@{ matchingLines = $lines.Count; rxBytes = $rx; txBytes = $tx; lines = $lines }
}

function Get-UidScopedLogs {
    $direct = Invoke-AdbCaptureWithTimeout -Name 'uid-logcat-direct' -TimeoutMilliseconds 30000 `
        -Arguments @('-s', $Serial, 'logcat', '-d', '-v', 'threadtime', "--uid=$uid")
    if ((Capture-CompletedSuccessfully $direct) -and
        $direct.output -notmatch 'Unrecognized Option') {
        return $direct.output
    }

    $deviceDirect = Invoke-AdbCaptureWithTimeout -Name 'uid-logcat-device' `
        -TimeoutMilliseconds 30000 `
        -Arguments @('-s', $Serial, 'shell', 'logcat', '-d', '-v', 'threadtime', "--uid=$uid")
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
        if ($processName -ne $package -and -not $processName.StartsWith("${package}:")) {
            continue
        }
        $pidValue = 0
        if (-not [int]::TryParse($parts[1], [ref]$pidValue)) { continue }
        $status = (Adb '-s' $Serial shell cat "/proc/$pidValue/status") -join "`n"
        if ($status -match "(?m)^Uid:\s+$uid(?:\s|$)") {
            $verifiedPids += $pidValue
        }
    }
    $verifiedPids = @($verifiedPids | Sort-Object -Unique)
    if ($verifiedPids.Count -eq 0) {
        throw "Could not resolve any live processes for exact package UID $uid."
    }

    Remove-Item -Force -ErrorAction SilentlyContinue `
        (Join-Path $OutputDirectory 'uid-logcat-full-fallback-stdout.txt'), `
        (Join-Path $OutputDirectory 'uid-logcat-full-fallback-stderr.txt')
    $fullLogText = (Adb '-s' $Serial logcat '-d' '-v' 'threadtime') -join "`n"
    $pidSet = @{}
    foreach ($pidValue in $verifiedPids) { $pidSet[[string]$pidValue] = $true }
    $filtered = @($fullLogText -split "`n" | Where-Object {
        $match = [regex]::Match(
            $_,
            '^\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d+\s+(\d+)\s+(\d+)\s'
        )
        $match.Success -and $pidSet.ContainsKey($match.Groups[1].Value)
    })
    (@("# Fallback: verified exact UID $uid for PIDs $($verifiedPids -join ',')") +
        $filtered) -join "`n"
}

try {
    $logcatClear = Invoke-AdbCaptureWithTimeout -Name 'logcat-clear' -Arguments @(
        '-s', $Serial, 'logcat', '-c'
    )
    if (-not (Capture-CompletedSuccessfully $logcatClear)) {
        Write-Warning "Logcat clear was unavailable; fixed canary matching remains authoritative."
    }
    Write-Host "Privacy probe: reading baseline network accounting"
    $before = Get-UidStats
    Write-Host "Privacy probe: baseline accounting supported=$networkAccountingSupported"
    Adb '-s' $Serial shell ime enable $sellerIme | Out-Null
    Adb '-s' $Serial shell ime set $sellerIme | Out-Null
    $caseResults = @()
    foreach ($case in $probeCases) {
        Write-Host "Privacy probe: exercising $($case.id) editor"
        $startArguments = @(
            '-s', $Serial, 'shell', 'am', 'start', '-W',
            '--activity-clear-top', '--activity-single-top',
            '-n', $activity,
            '--ei', 'inputType', [string]$case.inputType,
            '--ei', 'imeOptions', [string]$case.imeOptions,
            '--es', 'hintText', $case.hint
        )
        if (-not [string]::IsNullOrWhiteSpace($case.privateImeOptions)) {
            $startArguments += @(
                '--es', 'privateImeOptions', $case.privateImeOptions
            )
        }
        Adb @startArguments | Out-Null
        Start-Sleep -Seconds 1
        # `input text` is resolution-independent. The focused editor and IME still
        # receive the real selection/context updates that drive candidate/privacy
        # policy, unlike the former hard-coded tap coordinates.
        Adb '-s' $Serial shell input text $case.canary | Out-Null
        Start-Sleep -Seconds 1

        $remoteXml = "/sdcard/seller-privacy-$($case.id).xml"
        $localXml = Join-Path $OutputDirectory "$($case.id)-window.xml"
        Adb '-s' $Serial shell uiautomator dump $remoteXml | Out-Null
        Adb '-s' $Serial pull '-q' $remoteXml $localXml | Out-Null
        $windowXml = Get-Content -Raw -LiteralPath $localXml
        $visibleTextFound = $windowXml -match [regex]::Escape($case.canary)
        $passwordNodeFound = $windowXml -match 'password="true"'
        $modeVerified = if ($case.expectVisibleText) {
            $visibleTextFound
        } elseif ($case.expectPasswordNode) {
            $passwordNodeFound -and -not $visibleTextFound
        } else {
            $true
        }
        $caseResults += [ordered]@{
            id = $case.id
            inputType = $case.inputType
            privateImeOptions = $case.privateImeOptions
            canary = $case.canary
            visibleTextFound = $visibleTextFound
            passwordNodeFound = $passwordNodeFound
            editorModeVerified = $modeVerified
            windowXml = $localXml
        }
        if (-not $modeVerified) {
            throw "Privacy probe could not verify the $($case.id) editor input."
        }
        Write-Host "Privacy probe: verified $($case.id) editor"
    }
    Start-Sleep -Seconds 2
    Write-Host "Privacy probe: reading final network accounting"
    $after = Get-UidStats
    Write-Host "Privacy probe: collecting UID-scoped logcat"
    $logs = Get-UidScopedLogs
    Write-Host "Privacy probe: UID-scoped logcat collected"
    $logs | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'seller-uid-logcat.txt')
    $canaryOccurrences = 0
    foreach ($case in $probeCases) {
        $canaryOccurrences += ([regex]::Matches(
            $logs,
            [regex]::Escape($case.canary)
        )).Count
    }
    $rxDelta = if ($networkAccountingSupported) { $after.rxBytes - $before.rxBytes } else { $null }
    $txDelta = if ($networkAccountingSupported) { $after.txBytes - $before.txBytes } else { $null }
    $summary = [ordered]@{
        capturedAt = (Get-Date).ToString('o'); serial = $Serial; uid = $uid
        canaries = @($probeCases | ForEach-Object { $_.canary })
        cases = $caseResults
        networkAccountingSupported = $networkAccountingSupported
        networkAccountingReason = $networkAccountingReason
        beforeRxBytes = $before.rxBytes; afterRxBytes = $after.rxBytes
        beforeTxBytes = $before.txBytes; afterTxBytes = $after.txBytes
        rxDelta = $rxDelta; txDelta = $txDelta
        beforeMatchingLines = $before.matchingLines; afterMatchingLines = $after.matchingLines
        canaryOccurrencesInUidLog = $canaryOccurrences
    }
    $summary | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'summary.json')
    @($before.lines + $after.lines) | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'uid-netstats-lines.txt')
    if ($summary.networkAccountingSupported -and
        ($summary.rxDelta -ne 0 -or $summary.txDelta -ne 0)) {
        throw "Seller UID network bytes changed during synthetic typing."
    }
    if ($summary.canaryOccurrencesInUidLog -ne 0) { throw "Synthetic typed canary appeared in Seller UID logs." }
} finally {
    $restoreIme = if ($originalIme -and $originalIme -ne 'null') { $originalIme } else { $gboardIme }
    Adb '-s' $Serial shell ime set $restoreIme | Out-Null
}

Write-Host "Privacy/network evidence captured under $OutputDirectory"
