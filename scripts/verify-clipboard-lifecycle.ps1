[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = "Stop"
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

function Capture-Window([string]$Name) {
    $remote = "/sdcard/seller-clipboard-$Name.xml"
    $local = Join-Path $OutputDirectory "$Name-window.xml"
    Adb '-s' $Serial shell uiautomator dump $remote | Out-Null
    Adb '-s' $Serial pull '-q' $remote $local | Out-Null
    $local
}

function Invoke-BoundsTap([System.Text.RegularExpressions.Match]$Bounds) {
    $x = [int](([int]$Bounds.Groups[1].Value + [int]$Bounds.Groups[3].Value) / 2)
    $y = [int](([int]$Bounds.Groups[2].Value + [int]$Bounds.Groups[4].Value) / 2)
    Adb '-s' $Serial shell input tap $x $y | Out-Null
}

function Focus-Editor([string]$Name) {
    foreach ($attempt in 1..4) {
        $local = Capture-Window "$Name-focus"
        $xml = Get-Content -Raw -LiteralPath $local
        $node = [regex]::Match(
            $xml,
            '<node[^>]*(?:hint|text)="IME benchmark editor"[^>]*/?>'
        )
        if ($node.Success) {
            $bounds = [regex]::Match(
                $node.Value,
                'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
            )
            if (-not $bounds.Success) { throw "Clipboard editor has no bounds in $local" }
            Invoke-BoundsTap $bounds
            Start-Sleep -Seconds 1
            return
        }
        $waitNode = [regex]::Match($xml, '<node[^>]*text="Wait"[^>]*/?>')
        if ($waitNode.Success) {
            $waitBounds = [regex]::Match(
                $waitNode.Value,
                'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
            )
            if ($waitBounds.Success) { Invoke-BoundsTap $waitBounds }
            Start-Sleep -Seconds 2
            continue
        }
        Start-Sleep -Seconds 1
    }
    throw "Could not locate clipboard editor in $local"
}

function Start-Editor([string]$Name, [int]$InputType) {
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $activity '--ei' 'inputType' ([string]$InputType) '--ei' 'imeOptions' '6' | Out-Null
    Start-Sleep -Milliseconds 500
    Focus-Editor $Name
}

function Await-State([scriptblock]$Predicate, [string]$Description) {
    $deadline = (Get-Date).AddSeconds(12)
    do {
        $snapshot = Call-Bridge 'snapshot'
        if (& $Predicate $snapshot) { return $snapshot }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Description. Last state: $snapshot"
}

function Observed-Changes([string]$State) {
    [long](Bundle-Value $State 'clipboard_observed_changes')
}

$originalShowImeWithHardKeyboard = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
$originalHistoryEnabled = $true
$haveOriginalHistorySetting = $false
try {
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Adb '-s' $Serial shell ime enable $ime | Out-Null
    Adb '-s' $Serial shell ime set $ime | Out-Null

    $attributionRoot = Join-Path $OutputDirectory 'network-attribution'
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Start -Serial $Serial -PackageName $package `
        -OutputDirectory $attributionRoot

    Start-Editor 'normal' 1
    $initial = Await-State {
        param($state)
        (Bundle-Value $state 'active') -eq 'true' -and
            (Bundle-Value $state 'sensitive') -eq 'false' -and
            (Bundle-Value $state 'clipboard_started') -eq 'true'
    } 'normal editor clipboard listener'
    $originalHistoryEnabled = (Bundle-Value $initial 'clipboard_history_enabled') -ne 'false'
    $haveOriginalHistorySetting = $true
    Call-Bridge 'enable_clipboard_history' | Out-Null
    $cleared = Call-Bridge 'clear_clipboard_history'
    if ((Bundle-Value $cleared 'clipboard_item_count') -ne '0') {
        throw "Clipboard history did not clear before the connected test: $cleared"
    }

    $beforeNormal = Observed-Changes $cleared
    Call-Bridge 'set_normal_clip' | Out-Null
    $normal = Await-State {
        param($state)
        (Observed-Changes $state) -gt $beforeNormal -and
            (Bundle-Value $state 'clipboard_item_count') -eq '1' -and
            (Bundle-Value $state 'contains_normal_clip') -eq 'true'
    } 'normal clipboard capture'

    Call-Bridge 'clear_clipboard_history' | Out-Null
    $disabled = Call-Bridge 'disable_clipboard_history'
    $beforeDisabled = Observed-Changes $disabled
    Call-Bridge 'set_disabled_clip' | Out-Null
    $disabledCapture = Await-State {
        param($state)
        (Observed-Changes $state) -gt $beforeDisabled -and
            (Bundle-Value $state 'clipboard_history_enabled') -eq 'false' -and
            (Bundle-Value $state 'clipboard_item_count') -eq '0'
    } 'disabled-history clipboard rejection'
    $reenabled = Call-Bridge 'enable_clipboard_history'
    if ((Bundle-Value $reenabled 'clipboard_item_count') -ne '0' -or
        (Bundle-Value $reenabled 'contains_disabled_clip') -ne 'false') {
        throw "Clipboard text was retained while history was disabled: $reenabled"
    }

    $beforeRecapture = Observed-Changes $reenabled
    Call-Bridge 'set_normal_clip' | Out-Null
    $recaptured = Await-State {
        param($state)
        (Observed-Changes $state) -gt $beforeRecapture -and
            (Bundle-Value $state 'contains_normal_clip') -eq 'true'
    } 'clipboard recapture after enabling history'

    $beforeMarked = Observed-Changes $recaptured
    Call-Bridge 'set_marked_sensitive_clip' | Out-Null
    $markedSensitive = Await-State {
        param($state)
        (Observed-Changes $state) -gt $beforeMarked -and
            (Bundle-Value $state 'clipboard_item_count') -eq '1' -and
            (Bundle-Value $state 'contains_marked_sensitive_clip') -eq 'false'
    } 'system-marked-sensitive clipboard rejection'

    Start-Editor 'password' 129
    $password = Await-State {
        param($state)
        (Bundle-Value $state 'sensitive') -eq 'true' -and
            (Bundle-Value $state 'clipboard_started') -eq 'false'
    } 'password editor clipboard shutdown'
    $stoppedChangeCount = Observed-Changes $password
    Call-Bridge 'set_password_clip' | Out-Null
    Start-Sleep -Seconds 1
    $afterPasswordClip = Call-Bridge 'snapshot'
    if ((Observed-Changes $afterPasswordClip) -ne $stoppedChangeCount -or
        (Bundle-Value $afterPasswordClip 'contains_password_clip') -ne 'false' -or
        (Bundle-Value $afterPasswordClip 'clipboard_item_count') -ne '1') {
        throw "Password-mode clipboard listener retained or observed a clip: $afterPasswordClip"
    }

    $finalClear = Call-Bridge 'clear_clipboard_history'
    if ((Bundle-Value $finalClear 'clipboard_item_count') -ne '0') {
        throw "Clipboard history did not clear after the connected test: $finalClear"
    }

    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Finish -Serial $Serial -PackageName $package `
        -OutputDirectory $attributionRoot -Canary @(
            'Connected clipboard order reference',
            'Connected marked sensitive clipboard',
            'Connected disabled clipboard',
            'Connected password clipboard'
        )
    $attribution = Get-Content -Raw -LiteralPath `
        (Join-Path $attributionRoot 'summary.json') | ConvertFrom-Json

    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        normalCapture = $normal
        disabledCapture = $disabledCapture
        markedSensitiveRejection = $markedSensitive
        passwordListenerStopped = $afterPasswordClip
        finalClear = $finalClear
        networkAccountingSupported = [bool]$attribution.networkAccountingSupported
        uidRxDelta = $attribution.rxDelta
        uidTxDelta = $attribution.txDelta
        controlledTextOccurrencesInUidLog = `
            [int]$attribution.canaryOccurrencesInUidLog
        claimBoundary = 'system ClipboardManager path on emulator; physical OEM UI remains pending'
    } | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'summary.json')
    Write-Host "Clipboard lifecycle passed: $OutputDirectory"
} finally {
    if ($haveOriginalHistorySetting) {
        Call-Bridge $(if ($originalHistoryEnabled) {
            'enable_clipboard_history'
        } else {
            'disable_clipboard_history'
        }) | Out-Null
        Call-Bridge 'clear_clipboard_history' | Out-Null
    }
    if ($originalShowImeWithHardKeyboard -eq 'null' -or
        [string]::IsNullOrWhiteSpace($originalShowImeWithHardKeyboard)) {
        Adb '-s' $Serial shell settings delete secure show_ime_with_hard_keyboard | Out-Null
    } else {
        Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard `
            $originalShowImeWithHardKeyboard | Out-Null
    }
}
