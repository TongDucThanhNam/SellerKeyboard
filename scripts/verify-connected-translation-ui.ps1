[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' } else { 'adb' }
$package = 'com.terasumi.sellerkeyboard'
$hostPackage = 'com.terasumi.sellerkeyboard.benchmarkhost'
$providerPackage = 'com.google.android.gms'
$ime = "$package/.service.SellerKeyboard"
$activity = "$hostPackage/.ImeBenchmarkActivity"
$imeBridge = "content://$package.ime-test-bridge"
$hostBridge = "content://$hostPackage.host-test-bridge"
$originalText = 'Thank you for your order'
$viTranslate = "D$([char]0x1ECB)ch"
$enToVi = "EN $([char]0x2192) VI"
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: $($Arguments -join ' ')`n$($result -join "`n")"
    }
    $result
}

function Call-Bridge([string]$Uri, [string]$Method) {
    ((Adb '-s' $Serial shell content call --uri $Uri --method $Method) -join "`n").Trim()
}

function Bundle-Value([string]$Bundle, [string]$Key) {
    $match = [regex]::Match($Bundle, "(?:\{|,\s*)$([regex]::Escape($Key))=([^,}]*)")
    if (-not $match.Success) { return $null }
    $match.Groups[1].Value.Trim()
}

function Capture-Window([string]$Name, [switch]$Screenshot) {
    $remoteXml = "/sdcard/translation-ui-$Name.xml"
    $localXml = Join-Path $OutputDirectory "$Name-window.xml"
    Adb '-s' $Serial shell uiautomator dump '--windows' $remoteXml | Out-Null
    Adb '-s' $Serial pull '-q' $remoteXml $localXml | Out-Null
    if ($Screenshot) {
        $remotePng = "/sdcard/translation-ui-$Name.png"
        $localPng = Join-Path $OutputDirectory "$Name.png"
        Adb '-s' $Serial shell screencap '-p' $remotePng | Out-Null
        Adb '-s' $Serial pull '-q' $remotePng $localPng | Out-Null
    }
    $localXml
}

function Find-Node([string]$Xml, [string[]]$Values) {
    foreach ($value in $Values) {
        $escaped = [regex]::Escape($value)
        $match = [regex]::Match(
            $Xml,
            '<node[^>]*(?:text|content-desc|hint)="' + $escaped +
                '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*/?>'
        )
        if ($match.Success) { return $match }
    }
    $null
}

function Invoke-NodeTap([System.Text.RegularExpressions.Match]$Node) {
    $x = [int](([int]$Node.Groups[1].Value + [int]$Node.Groups[3].Value) / 2)
    $y = [int](([int]$Node.Groups[2].Value + [int]$Node.Groups[4].Value) / 2)
    Adb '-s' $Serial shell input tap $x $y | Out-Null
}

function Await-Node([string[]]$Values, [string]$Name, [int]$Seconds = 20) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $local = Capture-Window $Name
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $local
        $node = Find-Node $xml $Values
        if ($null -ne $node) {
            return [pscustomobject]@{ Node = $node; Xml = $xml; Path = $local }
        }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for '$($Values -join "' or '")' in $local"
}

function Tap-Node([string[]]$Values, [string]$Name) {
    $found = Await-Node $Values $Name
    Invoke-NodeTap $found.Node
    Start-Sleep -Milliseconds 500
    $found.Path
}

function Wait-ImeState([string]$Key, [string[]]$Accepted, [int]$Seconds = 180) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $snapshot = Call-Bridge $imeBridge 'feature_attribution_snapshot'
        if ((Bundle-Value $snapshot $Key) -in $Accepted) { return $snapshot }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Key in [$($Accepted -join ', ')]. Last: $snapshot"
}

function Start-HostImeSession([int]$Seconds = 45) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $window = Capture-Window 'host-startup'
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $window
        if ($xml -match "System UI isn.t responding") {
            $wait = Find-Node $xml @('Wait')
            if ($null -ne $wait) {
                Invoke-NodeTap $wait
                Start-Sleep -Seconds 2
                continue
            }
        }
        $editor = Find-Node $xml @('IME benchmark editor')
        if ($null -ne $editor) {
            Invoke-NodeTap $editor
            Start-Sleep -Milliseconds 500
        }
        $snapshot = Call-Bridge $imeBridge 'feature_attribution_snapshot'
        if ((Bundle-Value $snapshot 'active') -eq 'true' -and
            (Bundle-Value $snapshot 'editor_package') -eq $hostPackage -and
            (Bundle-Value $snapshot 'sensitive') -eq 'false') {
            return $snapshot
        }
        Adb '-s' $Serial shell ime enable $ime | Out-Null
        Adb '-s' $Serial shell ime set $ime | Out-Null
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Timed out activating SellerKeyboard in the neutral host. Last: $snapshot"
}

function Wait-HostHash([string]$Expected, [string]$Name, [int]$Seconds = 20) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $snapshot = Call-Bridge $hostBridge 'snapshot'
        if ((Bundle-Value $snapshot 'text_sha256') -eq $Expected) { return $snapshot }
        Start-Sleep -Milliseconds 150
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for host text hash during $Name. Last: $snapshot"
}

function Sha256([string]$Value) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
    -join ($hash | ForEach-Object { $_.ToString('x2') })
}

function Start-Attribution([string]$PackageName, [string]$Directory) {
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Start -Serial $Serial -PackageName $PackageName `
        -OutputDirectory $Directory -NetworkExpectation Record
}

function Finish-Attribution(
    [string]$PackageName,
    [string]$Directory,
    [string[]]$Canary
) {
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Finish -Serial $Serial -PackageName $PackageName `
        -OutputDirectory $Directory -NetworkExpectation Record -Canary $Canary
    Get-Content -Raw -LiteralPath (Join-Path $Directory 'summary.json') | ConvertFrom-Json
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
    $active = Start-HostImeSession

    $seeded = Call-Bridge $hostBridge 'seed_translation_selection'
    if ((Bundle-Value $seeded 'seeded') -ne 'true' -or
        (Bundle-Value $seeded 'matches_original') -ne 'true' -or
        [int](Bundle-Value $seeded 'selection_start') -ne 0 -or
        [int](Bundle-Value $seeded 'selection_end') -ne $originalText.Length) {
        throw "Neutral host did not seed and select the controlled original: $seeded"
    }
    Start-Sleep -Milliseconds 500

    Tap-Node @('Translate', $viTranslate) 'open-translate' | Out-Null
    Await-Node @('Translation draft text') 'draft-from-selection' | Out-Null
    Tap-Node @($enToVi) 'direction-en-to-vi' | Out-Null

    $imeAttributionRoot = Join-Path $OutputDirectory 'network-attribution\ime'
    $providerAttributionRoot = Join-Path $OutputDirectory 'network-attribution\provider'
    Start-Attribution $package $imeAttributionRoot
    Start-Attribution $providerPackage $providerAttributionRoot

    Call-Bridge $imeBridge 'delete_en_to_vi_model' | Out-Null
    $deleted = Wait-ImeState 'translation_state' @('ModelDeleted', 'Error') 60
    if ((Bundle-Value $deleted 'translation_state') -ne 'ModelDeleted') {
        throw "Could not delete the EN-to-VI model fixture: $deleted"
    }
    Call-Bridge $imeBridge 'start_en_to_vi_ui_translation' | Out-Null
    $preview = Wait-ImeState 'translation_state' @('Preview', 'Error') 180
    if ((Bundle-Value $preview 'translation_state') -ne 'Preview' -or
        (Bundle-Value $preview 'translation_original_matches_ui_fixture') -ne 'true' -or
        (Bundle-Value $preview 'translation_direction') -ne 'EN_TO_VI') {
        throw "EN-to-VI translation did not reach the controlled preview: $preview"
    }
    $translatedHash = Bundle-Value $preview 'translation_translated_sha256'
    $combinedHash = Bundle-Value $preview 'translation_original_plus_translated_sha256'
    if ($translatedHash -notmatch '^[0-9a-f]{64}$' -or $combinedHash -notmatch '^[0-9a-f]{64}$') {
        throw "Translation preview hashes are invalid: $preview"
    }

    $previewUi = Await-Node @('Replace selection') 'preview-actions' 30
    $translationMatch = [regex]::Match(
        $previewUi.Xml,
        '<node[^>]*text="Translation: ([^"]*)"'
    )
    if (-not $translationMatch.Success) {
        throw "Translation preview text was absent from $($previewUi.Path)"
    }
    $translatedText = [System.Net.WebUtility]::HtmlDecode($translationMatch.Groups[1].Value)
    if ([string]::IsNullOrWhiteSpace($translatedText) -or
        (Sha256 $translatedText) -ne $translatedHash) {
        throw "Visible translation did not match the controller preview hash."
    }
    Capture-Window 'preview-passed' -Screenshot | Out-Null

    Tap-Node @('Insert') 'insert-translation' | Out-Null
    $inserted = Wait-HostHash $combinedHash 'insert translation'
    Capture-Window 'insert-passed' -Screenshot | Out-Null

    $reseeded = Call-Bridge $hostBridge 'seed_translation_selection'
    if ((Bundle-Value $reseeded 'seeded') -ne 'true' -or
        (Bundle-Value $reseeded 'matches_original') -ne 'true') {
        throw "Neutral host could not restore the selection for replace testing: $reseeded"
    }
    Tap-Node @('Translate', $viTranslate) 'reopen-translate' | Out-Null
    Await-Node @('Translation draft text') 'replace-draft-from-selection' | Out-Null
    Tap-Node @($enToVi) 'replace-direction-en-to-vi' | Out-Null
    Call-Bridge $imeBridge 'start_en_to_vi_ui_translation' | Out-Null
    $secondPreview = Wait-ImeState 'translation_state' @('Preview', 'Error') 60
    if ((Bundle-Value $secondPreview 'translation_state') -ne 'Preview' -or
        (Bundle-Value $secondPreview 'translation_translated_sha256') -ne $translatedHash) {
        throw "Repeated EN-to-VI preview was not deterministic: $secondPreview"
    }
    Await-Node @('Replace selection') 'replace-preview-actions' 30 | Out-Null
    Tap-Node @('Replace selection') 'replace-selection' | Out-Null
    $replaced = Wait-HostHash $translatedHash 'replace selection'
    Capture-Window 'replace-passed' -Screenshot | Out-Null

    Tap-Node @('Restore original') 'restore-original' | Out-Null
    $restored = Wait-HostHash (Sha256 $originalText) 'restore original'
    Capture-Window 'restore-passed' -Screenshot | Out-Null

    $canaries = @($originalText, $translatedText)
    Call-Bridge $imeBridge 'cancel_translation_attribution' | Out-Null
    $final = Wait-ImeState 'translation_state' @('Idle') 20
    $imeAttribution = Finish-Attribution $package $imeAttributionRoot $canaries
    $providerAttribution = Finish-Attribution `
        $providerPackage $providerAttributionRoot $canaries

    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        targetPackage = $hostPackage
        targetIsDistinctPackage = $true
        direction = 'EN_TO_VI'
        sourceSelectedFromTargetEditor = $true
        previewVerified = $true
        visiblePreviewMatchedControllerHash = $true
        replacedSelection = (Bundle-Value $replaced 'text_sha256') -eq $translatedHash
        restoredOriginal = (Bundle-Value $restored 'matches_original') -eq 'true'
        insertedAfterOriginalOnNewLine = `
            (Bundle-Value $inserted 'text_sha256') -eq $combinedHash
        restoredIdle = (Bundle-Value $final 'translation_state') -eq 'Idle'
        translatedUtf16Length = $translatedText.Length
        translatedSha256 = $translatedHash
        imeAttribution = $imeAttribution
        providerAttribution = $providerAttribution
        previewWindow = $previewUi.Path
        claimBoundary = 'real API 36 Compose IME review UI, ML Kit EN-to-VI translation, and distinct-package editor mutation; named third-party and physical acceptance remain pending'
    } | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'summary.json')
    Write-Host "Connected EN-to-VI translation UI passed: $OutputDirectory"
} finally {
    try { Call-Bridge $imeBridge 'cancel_translation_attribution' | Out-Null } catch { }
    if ($originalShowImeWithHardKeyboard -eq 'null' -or
        [string]::IsNullOrWhiteSpace($originalShowImeWithHardKeyboard)) {
        Adb '-s' $Serial shell settings delete secure show_ime_with_hard_keyboard | Out-Null
    } else {
        Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard `
            $originalShowImeWithHardKeyboard | Out-Null
    }
}
