[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" } else { "adb" }
$package = "com.terasumi.sellerkeyboard"
$ime = "$package/.service.SellerKeyboard"
$activity = "$package/.benchmark.ImeBenchmarkActivity"
$bridge = "content://$package.ime-test-bridge"
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
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

function Await-Snapshot([scriptblock]$Predicate) {
    $deadline = (Get-Date).AddSeconds(12)
    do {
        $snapshot = Call-Bridge 'snapshot'
        if (& $Predicate $snapshot) { return $snapshot }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for IME bridge state. Last result: $snapshot"
}

function Focus-Editor([string]$Name) {
    $remote = "/sdcard/seller-ime-bridge-$Name.xml"
    $local = Join-Path $OutputDirectory "$Name-window.xml"
    foreach ($attempt in 1..5) {
        Adb '-s' $Serial shell uiautomator dump $remote | Out-Null
        Adb '-s' $Serial pull '-q' $remote $local | Out-Null
        $xml = Get-Content -Raw -LiteralPath $local
        $match = [regex]::Match(
            $xml,
            'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
        )
        if ($match.Success) { break }
        $wait = [regex]::Match(
            $xml,
            'text="Wait"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
        )
        if ($wait.Success) {
            $waitX = [int](([int]$wait.Groups[1].Value + [int]$wait.Groups[3].Value) / 2)
            $waitY = [int](([int]$wait.Groups[2].Value + [int]$wait.Groups[4].Value) / 2)
            Adb '-s' $Serial shell input tap $waitX $waitY | Out-Null
        }
        Start-Sleep -Seconds 2
    }
    if (-not $match.Success) {
        throw "Could not locate the benchmark editor after retries in $local"
    }
    $x = [int](
        ([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2
    )
    $y = [int](
        ([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2
    )
    Adb '-s' $Serial shell input tap $x $y | Out-Null
    Start-Sleep -Seconds 1
}

$originalShowImeWithHardKeyboard = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
try {
Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
Adb '-s' $Serial shell ime enable $ime | Out-Null
Adb '-s' $Serial shell ime set $ime | Out-Null
Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
    '-n' $activity '--ei' 'inputType' '1' '--ei' 'imeOptions' '6' | Out-Null
Start-Sleep -Seconds 1
Focus-Editor 'normal'
Adb '-s' $Serial shell input text 'bridgeprobe' | Out-Null

$normal = Await-Snapshot {
    param($state)
    (Bundle-Value $state 'active') -eq 'true' -and
        (Bundle-Value $state 'sensitive') -eq 'false' -and
        [long](Bundle-Value $state 'session_id') -gt 0
}
$seeded = Call-Bridge 'seed_normal_state'
if ((Bundle-Value $seeded 'inline_append_accepted') -ne 'true') {
    throw "Normal-session inline seed was rejected: $seeded"
}
$populated = Await-Snapshot {
    param($state)
    [int](Bundle-Value $state 'candidate_count') -gt 0 -and
        [int](Bundle-Value $state 'completion_count') -eq 1 -and
        [int](Bundle-Value $state 'inline_count') -eq 1 -and
        (Bundle-Value $state 'clipboard_started') -eq 'true'
}

Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
    '-n' $activity '--ei' 'inputType' '129' '--ei' 'imeOptions' '6' | Out-Null
Start-Sleep -Milliseconds 500
Focus-Editor 'password'
$sensitive = Await-Snapshot {
    param($state)
    (Bundle-Value $state 'sensitive') -eq 'true'
}
$requiredSensitiveState = [ordered]@{
    active_surface = 'KEYBOARD'
    candidate_source = 'NONE'
    candidate_count = '0'
    completion_count = '0'
    inline_count = '0'
    seller_count = '0'
    clipboard_started = 'false'
    voice_idle = 'true'
    translation_idle = 'true'
}
foreach ($entry in $requiredSensitiveState.GetEnumerator()) {
    $actual = Bundle-Value $sensitive $entry.Key
    if ($actual -ne $entry.Value) {
        throw "Sensitive IME state '$($entry.Key)' expected '$($entry.Value)', got '$actual': $sensitive"
    }
}

$late = Call-Bridge 'send_late_results'
if ((Bundle-Value $late 'inline_append_accepted') -ne 'false' -or
    (Bundle-Value $late 'active_surface') -ne 'KEYBOARD' -or
    (Bundle-Value $late 'completion_count') -ne '0' -or
    (Bundle-Value $late 'inline_count') -ne '0') {
    throw "Sensitive field accepted stale or prohibited state: $late"
}

[ordered]@{
    generatedAt = (Get-Date).ToString('o')
    overall = 'PASS'
    serial = $Serial
    normalSession = $normal
    populatedSession = $populated
    sensitiveSession = $sensitive
    lateResultSession = $late
    claimBoundary = 'debug-build connected IME state; not physical-device evidence'
} | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'summary.json')

Write-Host "Sensitive IME transition passed: $OutputDirectory"
} finally {
    if ($originalShowImeWithHardKeyboard -eq 'null' -or
        [string]::IsNullOrWhiteSpace($originalShowImeWithHardKeyboard)) {
        Adb '-s' $Serial shell settings delete secure show_ime_with_hard_keyboard | Out-Null
    } else {
        Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard `
            $originalShowImeWithHardKeyboard | Out-Null
    }
}
