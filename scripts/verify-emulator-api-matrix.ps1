[CmdletBinding()]
param(
    [ValidateSet(26, 30, 31, 34, 35)]
    [int[]]$ApiLevels = @(26, 30, 31, 34, 35),
    [string]$OutputDirectory = "",
    [switch]$ReusePassingEvidence
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdkRoot = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($sdkRoot)) { throw 'ANDROID_HOME is not set.' }

$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $sdkRoot 'emulator\emulator.exe'
$gradle = Join-Path $root 'gradlew.bat'
$matrixRoot = if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    Join-Path $root 'docs\evidence\verification\emulator-api-matrix\latest'
} else {
    $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDirectory)
}
New-Item -ItemType Directory -Force -Path $matrixRoot | Out-Null

function Get-ConnectedEmulatorPorts {
    $ports = @()
    foreach ($line in @(& $adb devices)) {
        if ($line -match '^emulator-(\d+)\s+') { $ports += [int]$Matches[1] }
    }
    return $ports
}

function Get-FreeEmulatorPort {
    $used = @(Get-ConnectedEmulatorPorts)
    foreach ($port in 5554..5584 | Where-Object { $_ % 2 -eq 0 }) {
        if ($port -notin $used) { return $port }
    }
    throw 'No free emulator console port is available in 5554..5584.'
}

function Wait-ForBoot([string]$Serial, [datetime]$Deadline) {
    do {
        Start-Sleep -Seconds 2
        $previousErrorActionPreference = $ErrorActionPreference
        try {
            # A newly launched emulator appears as "offline" before it becomes a
            # usable device. Treat that as expected polling state, not a failure.
            $ErrorActionPreference = 'Continue'
            $state = ((& $adb -s $Serial get-state 2>$null) -join '').Trim()
            $boot = if ($state -eq 'device') {
                ((& $adb -s $Serial shell getprop sys.boot_completed 2>$null) -join '').Trim()
            } else { '' }
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
    } until ($boot -eq '1' -or (Get-Date) -gt $Deadline)
    if ($boot -ne '1') { throw "Emulator '$Serial' did not finish booting before the deadline." }
    & $adb -s $Serial shell input keyevent 82 | Out-Null
    $packageDeadline = (Get-Date).AddMinutes(1)
    do {
        $packageReady = ((& $adb -s $Serial shell pm path android 2>$null) -join '').Trim()
        if ($packageReady -notmatch '^package:') { Start-Sleep -Seconds 1 }
    } until ($packageReady -match '^package:' -or (Get-Date) -gt $packageDeadline)
    if ($packageReady -notmatch '^package:') { throw "Package manager was not ready on '$Serial'." }
}

$buildLog = Join-Path $matrixRoot 'build.log'
& $gradle ':app:assembleDebug' ':app:assembleDebugAndroidTest' `
    ':benchmark-host:assembleBenchmark' '--console=plain' 2>&1 |
    Tee-Object -FilePath $buildLog | Write-Host
if ($LASTEXITCODE -ne 0) { throw 'API matrix APK build failed.' }

$initialSerials = @(& $adb devices | ForEach-Object {
    if ($_ -match '^(emulator-\d+)\s+device$') { $Matches[1] }
})
$testApk = Get-Item -LiteralPath (Join-Path $root `
    'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
$runs = @()
foreach ($api in $ApiLevels) {
    $avdName = "SellerKeyboardApi$api"
    $runRoot = Join-Path $root "docs\evidence\verification\emulator-connected\$avdName\latest"
    New-Item -ItemType Directory -Force -Path $runRoot | Out-Null
    $existingSummaryPath = Join-Path $runRoot 'summary.json'
    if ($ReusePassingEvidence -and (Test-Path -LiteralPath $existingSummaryPath)) {
        $existingFile = Get-Item -LiteralPath $existingSummaryPath
        $existing = Get-Content -Raw -LiteralPath $existingSummaryPath | ConvertFrom-Json
        if ($existingFile.LastWriteTimeUtc -ge $testApk.LastWriteTimeUtc -and
            $existing.overall -eq 'PASS' -and [int]$existing.api -eq $api -and
            [int]$existing.results -eq 22 -and [int]$existing.optionalBackendSkips -eq 2) {
            $runs += [ordered]@{
                api = $api
                avd = $avdName
                serial = $existing.serial
                overall = 'PASS'
                results = 22
                optionalBackendSkips = 2
                durationSeconds = 0
                evidence = "../../emulator-connected/$avdName/latest/summary.json"
                reused = $true
                error = $null
            }
            continue
        }
    }
    $port = Get-FreeEmulatorPort
    $serial = "emulator-$port"
    $process = $null
    $startedAt = Get-Date
    $errorMessage = $null
    try {
        $stdout = Join-Path $runRoot 'emulator-stdout.txt'
        $stderr = Join-Path $runRoot 'emulator-stderr.txt'
        $process = Start-Process -FilePath $emulator -ArgumentList @(
            '-avd', $avdName,
            '-port', $port,
            '-no-window', '-no-audio', '-no-boot-anim',
            '-no-snapshot-load', '-no-snapshot-save',
            '-gpu', 'swiftshader_indirect'
        ) -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
            -WindowStyle Hidden -PassThru
        Wait-ForBoot -Serial $serial -Deadline (Get-Date).AddMinutes(4)
        $actualAvd = ((& $adb -s $serial shell getprop ro.boot.qemu.avd_name) -join '').Trim()
        if ([string]::IsNullOrWhiteSpace($actualAvd)) {
            $actualAvd = @(& $adb -s $serial emu avd name) |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and $_ -ne 'OK' } |
                Select-Object -First 1
            $actualAvd = "$actualAvd".Trim()
        }
        if ($actualAvd -ne $avdName) {
            throw "Serial '$serial' booted '$actualAvd', expected '$avdName'."
        }
        & (Join-Path $PSScriptRoot 'verify-emulator-connected.ps1') `
            -Serial $serial -OutputDirectory $runRoot -SkipBuild
        $summary = Get-Content -Raw -LiteralPath (Join-Path $runRoot 'summary.json') |
            ConvertFrom-Json
        $runs += [ordered]@{
            api = $api
            avd = $avdName
            serial = $serial
            overall = $summary.overall
            results = $summary.results
            optionalBackendSkips = $summary.optionalBackendSkips
            durationSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
            evidence = "../../emulator-connected/$avdName/latest/summary.json"
            reused = $false
            error = $null
        }
    } catch {
        $errorMessage = $_.Exception.Message
        $runs += [ordered]@{
            api = $api
            avd = $avdName
            serial = $serial
            overall = 'FAIL'
            results = $null
            optionalBackendSkips = $null
            durationSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
            evidence = "../../emulator-connected/$avdName/latest/summary.json"
            reused = $false
            error = $errorMessage
        }
    } finally {
        if ($serial -notin $initialSerials) {
            $previousErrorActionPreference = $ErrorActionPreference
            try {
                $ErrorActionPreference = 'Continue'
                & $adb -s $serial emu kill 2>&1 | Out-Null
            } finally {
                $ErrorActionPreference = $previousErrorActionPreference
            }
            $disconnectDeadline = (Get-Date).AddSeconds(30)
            do {
                Start-Sleep -Milliseconds 500
                $stillConnected = $serial -in @(& $adb devices | ForEach-Object {
                    if ($_ -match '^(emulator-\d+)\s+') { $Matches[1] }
                })
            } until (-not $stillConnected -or (Get-Date) -gt $disconnectDeadline)
        }
        if ($process) {
            $process.Refresh()
            if (-not $process.HasExited) {
                Stop-Process -Id $process.Id -Force
            }
        }
    }
}

$failed = @($runs | Where-Object { $_.overall -ne 'PASS' })
$matrix = [ordered]@{
    generatedAt = (Get-Date).ToString('o')
    overall = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }
    evidenceClass = 'emulator API compatibility; not physical-device or OEM evidence'
    preservedInitialEmulators = $initialSerials
    apiLevels = @($ApiLevels)
    passCount = @($runs | Where-Object { $_.overall -eq 'PASS' }).Count
    failCount = $failed.Count
    runs = $runs
}
$matrix | ConvertTo-Json -Depth 6 |
    Set-Content -Encoding utf8 (Join-Path $matrixRoot 'summary.json')

if ($failed.Count -gt 0) {
    throw "API matrix failed for: $($failed.api -join ', '). See $matrixRoot."
}
Write-Host "Emulator API matrix passed: $matrixRoot"
