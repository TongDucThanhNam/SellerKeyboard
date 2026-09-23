[CmdletBinding()]
param(
    [string]$AvdName = "SellerKeyboardSmoke",
    [switch]$KeepRunning
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" } else { "adb" }
$emulator = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME "emulator\emulator.exe" } else { "emulator" }
$reportRoot = if ($AvdName -eq 'SellerKeyboardSmoke') {
    Join-Path $root "docs\evidence\verification\emulator-connected\latest"
} else {
    Join-Path $root "docs\evidence\verification\emulator-connected\$AvdName\latest"
}
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null

$startedHere = $false
$serial = ""
try {
    $connected = @(& $adb devices | Where-Object { $_ -match '^emulator-\d+\s+device$' })
    if ($connected.Count -gt 1) {
        throw "More than one emulator is connected; stop extras or run verify-emulator-connected.ps1 with an explicit serial."
    }
    if ($connected.Count -eq 1) {
        $serial = ($connected[0] -split '\s+')[0]
        $connectedAvd = ((& $adb -s $serial shell getprop ro.boot.qemu.avd_name) -join '').Trim()
        if ([string]::IsNullOrWhiteSpace($connectedAvd)) {
            $connectedAvd = ((& $adb -s $serial emu avd name) -join "`n").Trim()
        }
        if ($connectedAvd -ne $AvdName) {
            throw "Connected emulator '$serial' is AVD '$connectedAvd', expected '$AvdName'."
        }
    } else {
        $stdout = Join-Path $reportRoot 'emulator-stdout.txt'
        $stderr = Join-Path $reportRoot 'emulator-stderr.txt'
        Start-Process -FilePath $emulator -ArgumentList @(
            '-avd', $AvdName, '-no-window', '-no-audio', '-no-boot-anim',
            '-no-snapshot-load', '-no-snapshot-save',
            '-gpu', 'swiftshader_indirect'
        ) -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
            -WindowStyle Hidden | Out-Null
        $startedHere = $true
        $deadline = (Get-Date).AddMinutes(2)
        do {
            Start-Sleep -Seconds 2
            $connected = @(& $adb devices | Where-Object { $_ -match '^emulator-\d+\s+device$' })
        } until ($connected.Count -eq 1 -or (Get-Date) -gt $deadline)
        if ($connected.Count -ne 1) { throw "AVD '$AvdName' did not connect within two minutes." }
        $serial = ($connected[0] -split '\s+')[0]
    }

    $deadline = (Get-Date).AddMinutes(2)
    do {
        Start-Sleep -Seconds 2
        $bootCompleted = ((& $adb -s $serial shell getprop sys.boot_completed 2>$null) -join '').Trim()
    } until ($bootCompleted -eq '1' -or (Get-Date) -gt $deadline)
    if ($bootCompleted -ne '1') { throw "Emulator '$serial' did not finish booting within two minutes." }
    & $adb -s $serial shell input keyevent 82 | Out-Null

    & (Join-Path $PSScriptRoot 'verify-emulator-connected.ps1') `
        -Serial $serial -OutputDirectory $reportRoot
} finally {
    if ($startedHere -and -not $KeepRunning -and $serial) {
        & $adb -s $serial emu kill 2>&1 | Out-Null
    }
}
