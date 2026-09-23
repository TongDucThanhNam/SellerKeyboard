[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$RemoteImageUrl = '',
    [string]$RejectedProductionImageUrl = ''
)

$ErrorActionPreference = "Stop"
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" } else { "adb" }
$package = "com.terasumi.sellerkeyboard"
$targetPackage = "com.terasumi.sellerkeyboard.benchmarkhost"
$ime = "$package/.service.SellerKeyboard"
$activity = "$targetPackage/.ImeBenchmarkActivity"
$bridge = "content://$package.ime-test-bridge"
$fallback = "Seller product | VND 100000 | https://example.invalid/product"
$acceptedMarker = "RICH_ACCEPTED:image/jpeg"
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: $($Arguments -join ' ')`n$($result -join "`n")"
    }
    $result
}

function Call-Bridge([string]$Method, [string]$Arg = '') {
    $arguments = @('-s', $Serial, 'shell', 'content', 'call', '--uri', $bridge, '--method', $Method)
    if (-not [string]::IsNullOrWhiteSpace($Arg)) { $arguments += @('--arg', $Arg) }
    ((Adb @arguments) -join "`n").Trim()
}

function Bundle-Value([string]$Bundle, [string]$Key) {
    $match = [regex]::Match($Bundle, "(?:\{|,\s*)$([regex]::Escape($Key))=([^,}]*)")
    if (-not $match.Success) { return $null }
    $match.Groups[1].Value.Trim()
}

function Capture-Window([string]$Name) {
    $remote = "/sdcard/seller-rich-content-$Name.xml"
    $local = Join-Path $OutputDirectory "$Name-window.xml"
    Adb '-s' $Serial shell uiautomator dump $remote | Out-Null
    Adb '-s' $Serial pull '-q' $remote $local | Out-Null
    $local
}

function Focus-Editor([string]$Mode) {
    $hintPattern = [regex]::Escape("Rich content $Mode editor")
    foreach ($attempt in 1..4) {
        $local = Capture-Window "$Mode-focus"
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $local
        $node = [regex]::Match(
            $xml,
            '<node[^>]*(?:hint|text)="' + $hintPattern + '"[^>]*/?>'
        )
        if ($node.Success) {
            $bounds = [regex]::Match(
                $node.Value,
                'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
            )
            if (-not $bounds.Success) { throw "Rich-content editor has no bounds in $local" }
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
    throw "Could not locate the $Mode rich-content editor in $local"
}

function Invoke-BoundsTap([System.Text.RegularExpressions.Match]$Bounds) {
    $x = [int](([int]$Bounds.Groups[1].Value + [int]$Bounds.Groups[3].Value) / 2)
    $y = [int](([int]$Bounds.Groups[2].Value + [int]$Bounds.Groups[4].Value) / 2)
    Adb '-s' $Serial shell input tap $x $y | Out-Null
}

function Start-Target([string]$Mode) {
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $activity '--es' 'mode' $Mode | Out-Null
    Start-Sleep -Milliseconds 500
    Focus-Editor $Mode
}

function Await-ImeState([bool]$SupportsJpeg, [long]$AfterSessionId) {
    $deadline = (Get-Date).AddSeconds(12)
    do {
        $snapshot = Call-Bridge 'snapshot'
        if ((Bundle-Value $snapshot 'active') -eq 'true' -and
            [long](Bundle-Value $snapshot 'session_id') -gt $AfterSessionId -and
            ((Bundle-Value $snapshot 'supports_test_jpeg') -eq 'true') -eq $SupportsJpeg) {
            return $snapshot
        }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for rich-content IME state. Last result: $snapshot"
}

function Await-EditorText(
    [string]$Mode,
    [string]$Expected,
    [string]$CaptureName = $Mode
) {
    $deadline = (Get-Date).AddSeconds(12)
    do {
        $local = Capture-Window "$CaptureName-result"
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $local
        $textPattern = 'text="' + [regex]::Escape($Expected) + '"'
        if ($xml -match $textPattern) {
            return $local
        }
        Start-Sleep -Milliseconds 150
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for '$Expected' in the $Mode editor. Last XML: $local"
}

function Resolve-PackageUid([string]$PackageName) {
    $output = (Adb '-s' $Serial shell pm list packages '-U' $PackageName) -join "`n"
    $match = [regex]::Match(
        $output,
        "(?m)^package:$([regex]::Escape($PackageName))\s+uid:(\d+)\s*$"
    )
    if (-not $match.Success) { throw "Could not resolve UID for ${PackageName}: $output" }
    [int]$match.Groups[1].Value
}

$originalShowImeWithHardKeyboard = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
try {
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Adb '-s' $Serial shell ime enable $ime | Out-Null
    Adb '-s' $Serial shell ime set $ime | Out-Null

    $attributionRoot = Join-Path $OutputDirectory 'network-attribution'
    $isRemote = -not [string]::IsNullOrWhiteSpace($RemoteImageUrl)
    $networkExpectation = if ($isRemote) { 'Record' } else { 'Zero' }
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Start -Serial $Serial -PackageName $package `
        -OutputDirectory $attributionRoot -NetworkExpectation $networkExpectation

    $sellerUid = Resolve-PackageUid $package
    $targetUid = Resolve-PackageUid $targetPackage
    if ($sellerUid -eq $targetUid) {
        throw "Rich-content target must use a distinct UID; both resolved to $sellerUid"
    }

    $lastSessionId = 0L
    $results = @()
    foreach ($case in @(
        [pscustomobject]@{ mode = 'accept'; supports = $true; expected = $acceptedMarker },
        [pscustomobject]@{ mode = 'reject'; supports = $true; expected = $fallback },
        [pscustomobject]@{ mode = 'unsupported'; supports = $false; expected = $fallback }
    )) {
        Start-Target $case.mode
        $state = Await-ImeState $case.supports $lastSessionId
        $lastSessionId = [long](Bundle-Value $state 'session_id')
        $request = if ($isRemote) {
            Call-Bridge 'commit_remote_test_product' "$RemoteImageUrl-$($case.mode)"
        } else {
            Call-Bridge 'commit_test_product'
        }
        if ((Bundle-Value $request 'commit_requested') -ne 'true') {
            throw "Rich-content request was not dispatched for $($case.mode): $request"
        }
        $window = Await-EditorText $case.mode $case.expected
        $results += [ordered]@{
            mode = $case.mode
            advertisedJpeg = $case.supports
            expectedEditorText = $case.expected
            session = $state
            resultWindow = $window
        }
    }

    $productionSourcePolicy = $null
    if (-not [string]::IsNullOrWhiteSpace($RejectedProductionImageUrl)) {
        Start-Target 'accept'
        $state = Await-ImeState $true $lastSessionId
        $lastSessionId = [long](Bundle-Value $state 'session_id')
        $request = Call-Bridge 'commit_production_product' $RejectedProductionImageUrl
        if ((Bundle-Value $request 'commit_requested') -ne 'true') {
            throw "Production product request was not dispatched: $request"
        }
        $window = Await-EditorText 'accept' $fallback 'production-policy'
        $productionSourcePolicy = [ordered]@{
            source = $RejectedProductionImageUrl
            expected = 'text fallback without image fetch'
            editorAdvertisedJpeg = $true
            session = $state
            resultWindow = $window
        }
    }

    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Finish -Serial $Serial -PackageName $package `
        -OutputDirectory $attributionRoot -NetworkExpectation $networkExpectation `
        -Canary @($fallback, $RemoteImageUrl | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $attribution = Get-Content -Raw -LiteralPath `
        (Join-Path $attributionRoot 'summary.json') | ConvertFrom-Json

    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        sellerUid = $sellerUid
        targetPackage = $targetPackage
        targetUid = $targetUid
        crossUid = $true
        imageSource = if ($isRemote) { 'adb-reversed-http' } else { 'local-file' }
        remoteImageUrl = if ($isRemote) { $RemoteImageUrl } else { $null }
        cases = $results
        productionSourcePolicy = $productionSourcePolicy
        networkAccountingSupported = [bool]$attribution.networkAccountingSupported
        uidRxDelta = $attribution.rxDelta
        uidTxDelta = $attribution.txDelta
        controlledTextOccurrencesInUidLog = `
            [int]$attribution.canaryOccurrencesInUidLog
        claimBoundary = 'cross-UID neutral reference app; named third-party target apps remain pending'
    } | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'summary.json')
    Write-Host "Rich-content target matrix passed: $OutputDirectory"
} finally {
    if ($originalShowImeWithHardKeyboard -eq 'null' -or
        [string]::IsNullOrWhiteSpace($originalShowImeWithHardKeyboard)) {
        Adb '-s' $Serial shell settings delete secure show_ime_with_hard_keyboard | Out-Null
    } else {
        Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard `
            $originalShowImeWithHardKeyboard | Out-Null
    }
}
