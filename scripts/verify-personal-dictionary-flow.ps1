[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
} else {
    'adb'
}
$sellerPackage = 'com.terasumi.sellerkeyboard'
$hostPackage = 'com.terasumi.sellerkeyboard.benchmarkhost'
$sellerIme = "$sellerPackage/.service.SellerKeyboard"
$homeActivity = "$sellerPackage/.HomeActivity"
$hostActivity = "$hostPackage/.ImeBenchmarkActivity"
$sensitiveActivity = "$sellerPackage/.benchmark.ImeBenchmarkActivity"
$imeBridge = "content://$sellerPackage.ime-test-bridge"
$hostBridge = "content://$hostPackage.host-test-bridge"
$fixtureWord = 'parcelnova'
$fixturePrefix = 'parcel'
$chipText = "$fixtureWord $([char]0x00D7)"
$sensitiveHint = 'PersonalDictionarySensitiveEditor'

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
    $arguments = @('-s', $Serial, 'shell', 'content', 'call', '--uri', $Uri,
        '--method', $Method)
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
    $remoteXml = "/sdcard/personal-dictionary-$Name.xml"
    $localXml = Join-Path $OutputDirectory "$Name-window.xml"
    Adb '-s' $Serial shell uiautomator dump '--windows' $remoteXml | Out-Null
    Adb '-s' $Serial pull '-q' $remoteXml $localXml | Out-Null
    if ($Screenshot) {
        $remotePng = "/sdcard/personal-dictionary-$Name.png"
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

function Find-DescriptionNode([string]$Xml, [string]$Value) {
    [regex]::Match(
        $Xml,
        '<node[^>]*content-desc="' + [regex]::Escape($Value) +
            '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*/?>'
    )
}

function Invoke-NodeTap([System.Text.RegularExpressions.Match]$Node) {
    $x = [int](([int]$Node.Groups[1].Value + [int]$Node.Groups[3].Value) / 2)
    $y = [int](([int]$Node.Groups[2].Value + [int]$Node.Groups[4].Value) / 2)
    Adb '-s' $Serial shell input tap $x $y | Out-Null
}

function Await-Node(
    [string[]]$Values,
    [string]$Name,
    [int]$Seconds = 20
) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $path = Capture-Window $Name
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $path
        $node = Find-Node $xml $Values
        if ($null -ne $node) {
            return [pscustomobject]@{ Node = $node; Path = $path; Xml = $xml }
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for '$($Values -join "' or '")' in $path"
}

function Await-Description(
    [string]$Value,
    [string]$Name,
    [int]$Seconds = 20
) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $path = Capture-Window $Name
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $path
        $node = Find-DescriptionNode $xml $Value
        if ($node.Success) {
            return [pscustomobject]@{ Node = $node; Path = $path; Xml = $xml }
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for content description '$Value' in $path"
}

function Scroll-ToNode([string[]]$Values, [string]$Name, [int]$Attempts = 10) {
    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        $path = Capture-Window "$Name-$attempt"
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $path
        $node = Find-Node $xml $Values
        if ($null -ne $node) {
            return [pscustomobject]@{ Node = $node; Path = $path; Xml = $xml }
        }
        Adb '-s' $Serial shell input swipe 540 1500 540 700 400 | Out-Null
        Start-Sleep -Milliseconds 300
    }
    throw "Could not scroll to '$($Values -join "' or '")'"
}

function Assert-Value([string]$Actual, [string]$Expected, [string]$Context) {
    if ($Actual -ne $Expected) {
        throw "$Context expected '$Expected' but was '$Actual'"
    }
}

function Await-BridgeState(
    [string]$Key,
    [string]$Expected,
    [string]$Name,
    [int]$Seconds = 20
) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $bundle = Call-Bridge $imeBridge 'snapshot'
        if ((Bundle-Value $bundle $Key) -eq $Expected) { return $bundle }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for IME bridge $Key=$Expected during $Name. Last: $bundle"
}

function Start-HostEditor([string]$Name) {
    Adb '-s' $Serial shell ime enable $sellerIme | Out-Null
    Adb '-s' $Serial shell ime set $sellerIme | Out-Null
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $hostActivity | Out-Null
    $editor = Await-Node @('IME benchmark editor') "$Name-editor"
    Invoke-NodeTap $editor.Node
    $deadline = (Get-Date).AddSeconds(25)
    do {
        $bundle = Call-Bridge $imeBridge 'snapshot'
        if ((Bundle-Value $bundle 'active') -eq 'true' -and
            (Bundle-Value $bundle 'editor_package') -eq $hostPackage -and
            (Bundle-Value $bundle 'sensitive') -eq 'false') {
            Start-Sleep -Seconds 2
            Await-Description 'p' "$Name-keyboard-ready" | Out-Null
            return $bundle
        }
        Adb '-s' $Serial shell input tap 300 1000 | Out-Null
        Start-Sleep -Milliseconds 400
    } while ((Get-Date) -lt $deadline)
    throw "Timed out activating SellerKeyboard in the neutral host. Last: $bundle"
}

function Open-Dictionary([string]$Name) {
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $homeActivity | Out-Null
    Await-Node @('Home Page') "$Name-home" | Out-Null
    Scroll-ToNode @('Word') "$Name-dictionary"
}

function Add-FixtureThroughUi([string]$Name) {
    $wordField = Scroll-ToNode @('Word') "$Name-word"
    Invoke-NodeTap $wordField.Node
    Start-Sleep -Milliseconds 700
    Adb '-s' $Serial shell input text $fixtureWord | Out-Null
    $deadline = (Get-Date).AddSeconds(12)
    do {
        $path = Capture-Window "$Name-entered"
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $path
        $entered = [regex]::IsMatch(
            $xml,
            '<node[^>]*text="' + [regex]::Escape($fixtureWord) +
                '"[^>]*class="android.widget.EditText"[^>]*package="' +
                [regex]::Escape($sellerPackage) + '"[^>]*/?>'
        )
        if ($entered) { break }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)
    if (-not $entered) { throw "Home dictionary field did not contain the fixture word: $path" }
    $add = Await-Node @('Add') "$Name-add"
    Invoke-NodeTap $add.Node
    Start-Sleep -Milliseconds 500
    Adb '-s' $Serial shell input keyevent 4 | Out-Null
    Start-Sleep -Milliseconds 500
    $chip = Scroll-ToNode @($chipText) "$Name-chip"
    Capture-Window "$Name-added" -Screenshot | Out-Null
    $chip
}

function Type-VisibleKeys([string]$Value, [string]$Name) {
    foreach ($character in $Value.ToCharArray()) {
        $found = Await-Description ([string]$character) "$Name-$character"
        Invoke-NodeTap $found.Node
        Start-Sleep -Milliseconds 250
    }
}

function Force-StopSeller {
    Adb '-s' $Serial shell am force-stop $sellerPackage | Out-Null
    Start-Sleep -Milliseconds 500
}

if (-not $SkipBuild) {
    & (Join-Path $root 'gradlew.bat') :app:assembleDebug :benchmark-host:assembleBenchmark
    if ($LASTEXITCODE -ne 0) { throw 'Debug and benchmark-host APK build failed.' }
    $appApk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    $hostApk = Join-Path $root `
        'benchmark-host\build\outputs\apk\benchmark\benchmark-host-benchmark.apk'
    Adb '-s' $Serial install '-r' '-t' $hostApk | Out-Null
    Adb '-s' $Serial install '-r' '-t' $appApk | Out-Null
}

$deviceApi = [int](((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '').Trim())
if ($deviceApi -ne 36) { throw "This evidence gate requires API 36; found API $deviceApi" }

$originalIme = ((Adb '-s' $Serial shell settings get secure default_input_method) -join '').Trim()
$originalShowImeWithHardKeyboard = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
$fixtureCaptured = $false
$attributionFinished = $false
$restoreBundle = $null
$captureBundle = $null
$persistedBundle = $null
$candidateBundle = $null
$sensitiveBundle = $null
$attributionSummary = $null

try {
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Adb '-s' $Serial shell ime reset | Out-Null
    Start-HostEditor 'fixture-capture' | Out-Null

    $captureBundle = Call-Bridge $imeBridge 'capture_personal_dictionary_fixture'
    Assert-Value (Bundle-Value $captureBundle 'personal_dictionary_fixture_captured') `
        'true' 'Fixture capture'
    Assert-Value (Bundle-Value $captureBundle 'personal_dictionary_contains_fixture') `
        'false' 'Fixture precondition'
    Assert-Value (Bundle-Value $captureBundle 'personal_dictionary_learning_enabled') `
        'true' 'Learning fixture'
    $fixtureCaptured = $true

    $attributionRoot = Join-Path $OutputDirectory 'uid-attribution'
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Start -Serial $Serial -PackageName $sellerPackage `
        -OutputDirectory $attributionRoot -NetworkExpectation Zero

    Open-Dictionary 'add' | Out-Null
    Add-FixtureThroughUi 'add' | Out-Null

    Force-StopSeller
    Open-Dictionary 'persisted' | Out-Null
    Scroll-ToNode @($chipText) 'persisted-chip' | Out-Null
    Capture-Window 'persisted-after-force-stop' -Screenshot | Out-Null

    Start-HostEditor 'candidate' | Out-Null
    $persistedBundle = Call-Bridge $imeBridge 'personal_dictionary_snapshot'
    Assert-Value (Bundle-Value $persistedBundle 'personal_dictionary_contains_fixture') `
        'true' 'Persisted dictionary state'
    Assert-Value (Bundle-Value $persistedBundle 'personal_dictionary_learning_enabled') `
        'true' 'Persisted learning state'
    Assert-Value (Bundle-Value $persistedBundle 'language_mode') 'ENGLISH' `
        'Deterministic candidate language'

    Call-Bridge $hostBridge 'seed_seller_benchmark_empty' | Out-Null
    Start-Sleep -Seconds 1
    Type-VisibleKeys $fixturePrefix 'candidate-prefix'
    $candidate = Await-Description "Candidate $fixtureWord" 'candidate-visible'
    Capture-Window 'personal-candidate-visible' -Screenshot | Out-Null
    $candidateBundle = Call-Bridge $imeBridge 'snapshot'
    Assert-Value (Bundle-Value $candidateBundle 'candidate_source') `
        'TEXT_CANDIDATE' 'Personal candidate source'
    if ([int](Bundle-Value $candidateBundle 'candidate_count') -lt 1) {
        throw "Personal candidate count was empty: $candidateBundle"
    }
    Invoke-NodeTap $candidate.Node
    Start-Sleep -Milliseconds 500
    $committed = Call-Bridge $hostBridge 'snapshot'
    Assert-Value (Bundle-Value $committed 'text_sha256') (Sha256 $fixtureWord) `
        'Committed personal candidate text'
    Assert-Value (Bundle-Value $committed 'text_length') `
        ([string]$fixtureWord.Length) 'Committed personal candidate length'

    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $sensitiveActivity '--ei' inputType 129 '--ei' imeOptions 6 `
        '--es' hintText $sensitiveHint | Out-Null
    $sensitiveEditor = Await-Node @($sensitiveHint) 'sensitive-editor'
    Invoke-NodeTap $sensitiveEditor.Node
    $sensitiveBundle = Await-BridgeState 'sensitive' 'true' 'sensitive editor'
    Assert-Value (Bundle-Value $sensitiveBundle 'editor_package') $sellerPackage `
        'Sensitive editor package'
    Await-Description 'p' 'sensitive-keyboard-ready' | Out-Null
    Type-VisibleKeys $fixturePrefix 'sensitive-prefix'
    Start-Sleep -Milliseconds 600
    $sensitiveBundle = Call-Bridge $imeBridge 'snapshot'
    Assert-Value (Bundle-Value $sensitiveBundle 'sensitive') 'true' `
        'Sensitive field classification'
    Assert-Value (Bundle-Value $sensitiveBundle 'candidate_source') 'NONE' `
        'Sensitive candidate source'
    Assert-Value (Bundle-Value $sensitiveBundle 'candidate_count') '0' `
        'Sensitive candidate count'
    $sensitiveWindow = Capture-Window 'sensitive-suppression' -Screenshot
    $sensitiveXml = Get-Content -Raw -Encoding UTF8 -LiteralPath $sensitiveWindow
    if ($sensitiveXml -match [regex]::Escape("Candidate $fixtureWord") -or
        $sensitiveXml -match [regex]::Escape($fixtureWord)) {
        throw 'The personal dictionary word was exposed in the sensitive-field UI tree.'
    }

    Force-StopSeller
    Open-Dictionary 'remove' | Out-Null
    $removeChip = Scroll-ToNode @($chipText) 'remove-chip'
    Invoke-NodeTap $removeChip.Node
    Start-Sleep -Milliseconds 600
    $removeWindow = Capture-Window 'removed-through-ui' -Screenshot
    $removeXml = Get-Content -Raw -Encoding UTF8 -LiteralPath $removeWindow
    if ($null -ne (Find-Node $removeXml @($chipText))) {
        throw 'Personal dictionary chip remained after the Remove UI action.'
    }
    Force-StopSeller
    Open-Dictionary 'remove-persisted' | Out-Null
    $removePersistedWindow = Capture-Window 'remove-persisted' -Screenshot
    $removePersistedXml = Get-Content -Raw -Encoding UTF8 -LiteralPath `
        $removePersistedWindow
    if ($null -ne (Find-Node $removePersistedXml @($chipText))) {
        throw 'Removed personal dictionary word returned after process recreation.'
    }

    Add-FixtureThroughUi 'clear-setup' | Out-Null
    $clear = Scroll-ToNode @('Clear personal dictionary') 'clear-action'
    Invoke-NodeTap $clear.Node
    Start-Sleep -Milliseconds 600
    $clearWindow = Capture-Window 'cleared-through-ui' -Screenshot
    $clearXml = Get-Content -Raw -Encoding UTF8 -LiteralPath $clearWindow
    if ($null -ne (Find-Node $clearXml @($chipText))) {
        throw 'Personal dictionary chip remained after the Clear UI action.'
    }
    Force-StopSeller
    Open-Dictionary 'clear-persisted' | Out-Null
    $clearPersistedWindow = Capture-Window 'clear-persisted' -Screenshot
    $clearPersistedXml = Get-Content -Raw -Encoding UTF8 -LiteralPath `
        $clearPersistedWindow
    if ($null -ne (Find-Node $clearPersistedXml @($chipText))) {
        throw 'Cleared personal dictionary word returned after process recreation.'
    }

    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Finish -Serial $Serial -PackageName $sellerPackage `
        -OutputDirectory $attributionRoot -Canary @($fixtureWord) `
        -NetworkExpectation Zero
    $attributionFinished = $true
    $attributionSummary = Get-Content -Raw -LiteralPath `
        (Join-Path $attributionRoot 'summary.json') | ConvertFrom-Json
} finally {
    if ($fixtureCaptured) {
        Start-HostEditor 'fixture-restore' | Out-Null
        $restoreBundle = Call-Bridge $imeBridge 'restore_personal_dictionary_fixture'
        Assert-Value (Bundle-Value $restoreBundle 'personal_dictionary_fixture_restored') `
            'true' 'Fixture restore'
        Assert-Value (Bundle-Value $restoreBundle 'personal_dictionary_sha256') `
            (Bundle-Value $captureBundle 'personal_dictionary_original_sha256') `
            'Exact original dictionary restore'
        Assert-Value (Bundle-Value $restoreBundle 'personal_dictionary_count') `
            (Bundle-Value $captureBundle 'personal_dictionary_original_count') `
            'Original dictionary count restore'
        Assert-Value (Bundle-Value $restoreBundle 'personal_dictionary_learning_enabled') `
            (Bundle-Value $captureBundle 'personal_dictionary_original_learning') `
            'Original learning preference restore'
        Assert-Value (Bundle-Value $restoreBundle 'personal_dictionary_settings_language') `
            (Bundle-Value $captureBundle 'personal_dictionary_original_language') `
            'Original language preference restore'
    }
    if (-not [string]::IsNullOrWhiteSpace($originalIme) -and $originalIme -ne 'null') {
        Adb '-s' $Serial shell ime enable $originalIme | Out-Null
        Adb '-s' $Serial shell ime set $originalIme | Out-Null
    }
    if ($originalShowImeWithHardKeyboard -match '^\d+$') {
        Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard `
            $originalShowImeWithHardKeyboard | Out-Null
    } else {
        Adb '-s' $Serial shell settings delete secure show_ime_with_hard_keyboard | Out-Null
    }
}

if (-not $attributionFinished) {
    throw 'UID attribution did not finish.'
}

[ordered]@{
    overall = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    serial = $Serial
    api = $deviceApi
    fixtureWordSha256 = Sha256 $fixtureWord
    productUiAddVerified = $true
    persistenceAfterProcessRecreationVerified = $true
    realCandidatePipelineVerified = $true
    candidateCommittedExact = $true
    sensitiveFieldSuppressionVerified = $true
    productUiRemoveVerified = $true
    removePersistenceVerified = $true
    productUiClearVerified = $true
    clearPersistenceVerified = $true
    learningEnabledDuringFixture = $true
    deterministicLanguageMode = 'ENGLISH'
    originalDictionaryRestoredExactly = $true
    originalSettingsRestoredExactly = $true
    originalDictionaryCount = [int](Bundle-Value $captureBundle `
        'personal_dictionary_original_count')
    networkAccountingSupported = [bool]$attributionSummary.networkAccountingSupported
    uidRxDelta = $attributionSummary.rxDelta
    uidTxDelta = $attributionSummary.txDelta
    controlledWordOccurrencesInUidLog = `
        [int]$attributionSummary.canaryOccurrencesInUidLog
    artifacts = [ordered]@{
        addedUi = Join-Path $OutputDirectory 'add-added.png'
        persistedUi = Join-Path $OutputDirectory 'persisted-after-force-stop.png'
        candidateUi = Join-Path $OutputDirectory 'personal-candidate-visible.png'
        sensitiveUi = Join-Path $OutputDirectory 'sensitive-suppression.png'
        removedUi = Join-Path $OutputDirectory 'removed-through-ui.png'
        removePersistedUi = Join-Path $OutputDirectory 'remove-persisted.png'
        clearedUi = Join-Path $OutputDirectory 'cleared-through-ui.png'
        clearPersistedUi = Join-Path $OutputDirectory 'clear-persisted.png'
        attributionSummary = Join-Path $attributionRoot 'summary.json'
    }
} | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 `
    (Join-Path $OutputDirectory 'summary.json')

Write-Host "Personal dictionary emulator evidence passed: $OutputDirectory"
