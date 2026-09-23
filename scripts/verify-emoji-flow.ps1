[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [int]$ExpectedApi = 36,
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
$hostActivity = "$hostPackage/.ImeBenchmarkActivity"
$sensitiveActivity = "$sellerPackage/.benchmark.ImeBenchmarkActivity"
$imeBridge = "content://$sellerPackage.ime-test-bridge"
$hostBridge = "content://$hostPackage.host-test-bridge"
$searchQuery = 'shipping'
$searchEmoji = '🚚'
$baseVariantEmoji = '🙋'
$variantEmoji = '🙋🏽'
$sensitiveHint = 'EmojiSensitiveEditor'

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
    ((Adb '-s' $Serial shell content call '--uri' $Uri '--method' $Method) -join "`n").Trim()
}

function Bundle-Value([string]$Bundle, [string]$Key) {
    $match = [regex]::Match($Bundle, "(?:\{|,\s*)$([regex]::Escape($Key))=([^,}]*)")
    if (-not $match.Success) { return $null }
    $match.Groups[1].Value.Trim()
}

function Assert-Value([string]$Actual, [string]$Expected, [string]$Context) {
    if ($Actual -ne $Expected) {
        throw "$Context expected '$Expected' but was '$Actual'"
    }
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
    $remoteXml = "/sdcard/emoji-$Name.xml"
    $localXml = Join-Path $OutputDirectory "$Name-window.xml"
    Adb '-s' $Serial shell uiautomator dump '--windows' $remoteXml | Out-Null
    Adb '-s' $Serial pull '-q' $remoteXml $localXml | Out-Null
    if ($Screenshot) {
        $remotePng = "/sdcard/emoji-$Name.png"
        $localPng = Join-Path $OutputDirectory "$Name.png"
        Adb '-s' $Serial shell screencap '-p' $remotePng | Out-Null
        Adb '-s' $Serial pull '-q' $remotePng $localPng | Out-Null
    }
    $localXml
}

function Read-Window([string]$Path) {
    [xml](Get-Content -Raw -Encoding UTF8 -LiteralPath $Path)
}

function Find-Node([xml]$Document, [string[]]$Values) {
    foreach ($value in $Values) {
        foreach ($node in $Document.SelectNodes('//node')) {
            if ($node.GetAttribute('content-desc') -eq $value -or
                $node.GetAttribute('text') -eq $value -or
                $node.GetAttribute('hint') -eq $value) {
                return $node
            }
        }
    }
    $null
}

function Find-DescriptionNode([xml]$Document, [string]$Value) {
    foreach ($node in $Document.SelectNodes('//node')) {
        if ($node.GetAttribute('content-desc') -eq $Value) { return $node }
    }
    $null
}

function Clickable-Node([System.Xml.XmlElement]$Node) {
    $current = $Node
    while ($null -ne $current -and $current -is [System.Xml.XmlElement]) {
        if ($current.GetAttribute('clickable') -eq 'true') { return $current }
        $current = $current.ParentNode
    }
    $Node
}

function Node-Bounds([System.Xml.XmlElement]$Node) {
    $match = [regex]::Match(
        $Node.GetAttribute('bounds'),
        '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$'
    )
    if (-not $match.Success) { throw "Node has invalid bounds: $($Node.OuterXml)" }
    [pscustomobject]@{
        Left = [int]$match.Groups[1].Value
        Top = [int]$match.Groups[2].Value
        Right = [int]$match.Groups[3].Value
        Bottom = [int]$match.Groups[4].Value
    }
}

function Invoke-NodeTap([System.Xml.XmlElement]$Node) {
    $bounds = Node-Bounds (Clickable-Node $Node)
    $x = [int](($bounds.Left + $bounds.Right) / 2)
    $y = [int](($bounds.Top + $bounds.Bottom) / 2)
    Adb '-s' $Serial shell input tap $x $y | Out-Null
}

function Invoke-NodeLongPress([System.Xml.XmlElement]$Node) {
    $bounds = Node-Bounds $Node
    $x = [int](($bounds.Left + $bounds.Right) / 2)
    $y = [int](($bounds.Top + $bounds.Bottom) / 2)
    Adb '-s' $Serial shell input swipe $x $y $x $y 1200 | Out-Null
}

function Await-Node([string[]]$Values, [string]$Name, [int]$Seconds = 20) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $path = Capture-Window $Name
        $document = Read-Window $path
        $node = Find-Node $document $Values
        if ($null -ne $node) {
            return [pscustomobject]@{ Node = $node; Path = $path; Document = $document }
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for '$($Values -join "' or '")' in $path"
}

function Await-Description([string]$Value, [string]$Name, [int]$Seconds = 20) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $path = Capture-Window $Name
        $document = Read-Window $path
        $node = Find-DescriptionNode $document $Value
        if ($null -ne $node) {
            return [pscustomobject]@{ Node = $node; Path = $path; Document = $document }
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for content description '$Value' in $path"
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

function Assert-MinimumHeight(
    [System.Xml.XmlElement]$Node,
    [double]$Density,
    [double]$MinimumDp,
    [string]$Context
) {
    $bounds = Node-Bounds (Clickable-Node $Node)
    $heightDp = ($bounds.Bottom - $bounds.Top) * 160.0 / $Density
    if ($heightDp + 0.05 -lt $MinimumDp) {
        throw "$Context height was $heightDp dp; expected at least $MinimumDp dp"
    }
    $heightDp
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
            Start-Sleep -Seconds 1
            Await-Description 'p' "$Name-keyboard-ready" | Out-Null
            return $bundle
        }
        Invoke-NodeTap $editor.Node
        Start-Sleep -Milliseconds 400
    } while ((Get-Date) -lt $deadline)
    throw "Timed out activating SellerKeyboard in the neutral host. Last: $bundle"
}

function Open-Emoji([string]$Name) {
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        $path = Capture-Window "$Name-toolbar-$attempt"
        $document = Read-Window $path
        $emoji = Find-DescriptionNode $document 'Emoji'
        if ($null -ne $emoji) {
            Invoke-NodeTap $emoji
            return Await-Description 'Emoji search' "$Name-open"
        }
        $anchor = Find-Node $document @(
            'Keyboard', 'Products', 'Clipboard', 'Calculator', 'Translate', 'Voice Assistance'
        )
        if ($null -eq $anchor) { throw 'Could not locate the IME toolbar for emoji scrolling.' }
        $anchorBounds = Node-Bounds $anchor
        $rootBounds = Node-Bounds ($document.SelectNodes('//node') | Select-Object -First 1)
        $y = [int](($anchorBounds.Top + $anchorBounds.Bottom) / 2)
        Adb '-s' $Serial shell input swipe `
            ([int]($rootBounds.Right * 0.86)) $y ([int]($rootBounds.Right * 0.14)) $y 450 | Out-Null
        Start-Sleep -Milliseconds 350
    }
    throw 'Emoji did not become visible after scrolling the real IME toolbar.'
}

function Type-ThroughImeKeypad([string]$Value, [xml]$Document) {
    foreach ($character in $Value.ToCharArray()) {
        $node = Find-DescriptionNode $Document "Text entry key $character"
        if ($null -eq $node) { throw "Missing in-IME text key '$character'." }
        Invoke-NodeTap $node
        Start-Sleep -Milliseconds 120
    }
}

function Force-StopSeller {
    Adb '-s' $Serial shell am force-stop $sellerPackage | Out-Null
    Start-Sleep -Milliseconds 600
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
if ($deviceApi -ne $ExpectedApi) {
    throw "This evidence gate requires API $ExpectedApi; found API $deviceApi"
}
$densityOutput = (Adb '-s' $Serial shell wm density) -join "`n"
$densityMatches = [regex]::Matches($densityOutput, '(?:Physical|Override) density:\s*(\d+)')
if ($densityMatches.Count -eq 0) { throw "Could not determine display density: $densityOutput" }
$density = [double]$densityMatches[$densityMatches.Count - 1].Groups[1].Value

$originalIme = ((Adb '-s' $Serial shell settings get secure default_input_method) -join '').Trim()
$originalShowImeWithHardKeyboard = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
$fixtureCaptured = $false
$attributionFinished = $false
$captureBundle = $null
$restoreBundle = $null
$searchBundle = $null
$persistedBundle = $null
$variantBundle = $null
$clearedBundle = $null
$clearPersistedBundle = $null
$sensitiveBundle = $null
$attributionSummary = $null
$searchFieldHeightDp = 0.0
$keyHeightDp = 0.0
$searchResultHeightDp = 0.0
$clearHeightDp = 0.0

try {
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Adb '-s' $Serial shell ime reset | Out-Null
    Start-HostEditor 'fixture-capture' | Out-Null

    $captureBundle = Call-Bridge $imeBridge 'capture_emoji_recents_fixture'
    $fixtureCaptured = $true
    Assert-Value (Bundle-Value $captureBundle 'emoji_recents_fixture_captured') `
        'true' 'Emoji fixture capture'
    Assert-Value (Bundle-Value $captureBundle 'emoji_recents_count') '0' `
        'Deterministic empty recents precondition'
    Assert-Value (Bundle-Value $captureBundle 'emoji_variant_present') 'false' `
        'Deterministic base-variant precondition'

    $attributionRoot = Join-Path $OutputDirectory 'uid-attribution'
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Start -Serial $Serial -PackageName $sellerPackage `
        -OutputDirectory $attributionRoot -NetworkExpectation Zero

    Call-Bridge $hostBridge 'seed_seller_benchmark_empty' | Out-Null
    Open-Emoji 'search' | Out-Null
    $openWindow = Capture-Window 'emoji-open' -Screenshot
    $openDocument = Read-Window $openWindow
    $searchField = Find-DescriptionNode $openDocument 'Emoji search'
    $clearRecents = Find-Node $openDocument @('Clear recents')
    if ($null -eq $searchField -or $null -eq $clearRecents) {
        throw 'Emoji open state did not expose search and clear-recents semantics.'
    }
    $searchFieldHeightDp = Assert-MinimumHeight $searchField $density 48 'Emoji search field'
    $clearHeightDp = Assert-MinimumHeight $clearRecents $density 48 'Clear recents action'
    Invoke-NodeTap $searchField

    $keypad = Await-Description 'Text entry key s' 'touch-search-keypad'
    $keyHeightDp = Assert-MinimumHeight $keypad.Node $density 48 'In-IME search key'
    $semanticKeyCount = @(
        $keypad.Document.SelectNodes('//node') | Where-Object {
            $_.GetAttribute('content-desc').StartsWith('Text entry key ')
        }
    ).Count
    if ($semanticKeyCount -lt 29) {
        throw "Only $semanticKeyCount alphabetic in-IME search keys exposed semantics."
    }
    Capture-Window 'touch-search-keypad' -Screenshot | Out-Null
    Type-ThroughImeKeypad $searchQuery $keypad.Document
    $done = Find-DescriptionNode $keypad.Document 'Finish text entry'
    if ($null -eq $done) { throw 'In-IME search keypad did not expose its Done action.' }
    Invoke-NodeTap $done

    $searchResult = Await-Description "$searchEmoji emoji" 'search-result'
    $searchResultHeightDp = Assert-MinimumHeight `
        $searchResult.Node $density 48 'Emoji search result'
    Capture-Window 'search-result' -Screenshot | Out-Null
    Invoke-NodeTap $searchResult.Node
    Start-Sleep -Milliseconds 500
    $committedSearch = Call-Bridge $hostBridge 'snapshot'
    Assert-Value (Bundle-Value $committedSearch 'text_sha256') (Sha256 $searchEmoji) `
        'Search emoji exact commit'
    Assert-Value (Bundle-Value $committedSearch 'text_length') `
        ([string]$searchEmoji.Length) 'Search emoji UTF-16 length'
    $searchBundle = Call-Bridge $imeBridge 'emoji_recents_snapshot'
    Assert-Value (Bundle-Value $searchBundle 'emoji_recents_contains_fixture') `
        'true' 'Search selection recorded in recents'
    Assert-Value (Bundle-Value $searchBundle 'emoji_recents_count') '1' `
        'Single deterministic search recent'

    Force-StopSeller
    Start-HostEditor 'recent-persistence' | Out-Null
    Open-Emoji 'recent-persistence' | Out-Null
    Await-Node @('RECENTLY USED') 'recent-category' | Out-Null
    Await-Description "$searchEmoji emoji" 'recent-search-emoji' | Out-Null
    Capture-Window 'recent-persisted' -Screenshot | Out-Null
    $persistedBundle = Call-Bridge $imeBridge 'emoji_recents_snapshot'
    Assert-Value (Bundle-Value $persistedBundle 'emoji_recents_contains_fixture') `
        'true' 'Emoji recent after process recreation'
    Assert-Value (Bundle-Value $persistedBundle 'emoji_recents_count') '1' `
        'Emoji recent count after process recreation'

    Force-StopSeller
    Start-HostEditor 'variant' | Out-Null
    Call-Bridge $hostBridge 'seed_seller_benchmark_empty' | Out-Null
    Open-Emoji 'variant' | Out-Null
    $people = Await-Description 'PEOPLE' 'variant-people-category'
    Invoke-NodeTap $people.Node
    $baseVariant = Await-Description "$baseVariantEmoji emoji" 'variant-base'
    if ($baseVariant.Node.GetAttribute('long-clickable') -ne 'true') {
        throw 'Emoji2 base variant did not expose a long-click action.'
    }
    Invoke-NodeLongPress $baseVariant.Node
    $variant = Await-Description "$variantEmoji emoji" 'variant-popup'
    if ((Clickable-Node $variant.Node).GetAttribute('clickable') -ne 'true') {
        throw 'Emoji2 variant popup did not expose a clickable semantic action.'
    }
    Capture-Window 'variant-popup' -Screenshot | Out-Null
    Invoke-NodeTap $variant.Node
    Start-Sleep -Milliseconds 500
    $committedVariant = Call-Bridge $hostBridge 'snapshot'
    Assert-Value (Bundle-Value $committedVariant 'text_sha256') (Sha256 $variantEmoji) `
        'Variant emoji exact commit'
    Assert-Value (Bundle-Value $committedVariant 'text_length') `
        ([string]$variantEmoji.Length) 'Variant emoji UTF-16 length'
    $variantBundle = Call-Bridge $imeBridge 'emoji_recents_snapshot'
    if ([int](Bundle-Value $variantBundle 'emoji_recents_count') -lt 2) {
        throw "Variant selection did not join persisted search recents: $variantBundle"
    }

    $clearAction = Await-Node @('Clear recents') 'clear-recents-action'
    Invoke-NodeTap $clearAction.Node
    Start-Sleep -Milliseconds 700
    $clearedBundle = Call-Bridge $imeBridge 'emoji_recents_snapshot'
    Assert-Value (Bundle-Value $clearedBundle 'emoji_recents_count') '0' `
        'Clear recents product UI'
    Assert-Value (Bundle-Value $clearedBundle 'emoji_recents_contains_fixture') `
        'false' 'Controlled recent removed through product UI'
    Capture-Window 'recents-cleared' -Screenshot | Out-Null

    Force-StopSeller
    Start-HostEditor 'clear-persistence' | Out-Null
    Open-Emoji 'clear-persistence' | Out-Null
    Await-Node @("You haven't used any emojis yet") 'clear-empty-message' | Out-Null
    $clearPersistedWindow = Capture-Window 'recents-clear-persisted' -Screenshot
    $clearPersistedDocument = Read-Window $clearPersistedWindow
    $clearPersistedBundle = Call-Bridge $imeBridge 'emoji_recents_snapshot'
    Assert-Value (Bundle-Value $clearPersistedBundle 'emoji_recents_count') '0' `
        'Clear recents persistence after process recreation'

    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $sensitiveActivity '--ei' inputType 129 '--ei' imeOptions 6 `
        '--es' hintText $sensitiveHint | Out-Null
    $sensitiveEditor = Await-Node @($sensitiveHint) 'sensitive-editor'
    Invoke-NodeTap $sensitiveEditor.Node
    $sensitiveBundle = Await-BridgeState 'sensitive' 'true' 'sensitive editor'
    Assert-Value (Bundle-Value $sensitiveBundle 'active_surface') 'KEYBOARD' `
        'Sensitive editor forced keyboard surface'
    Await-Description 'p' 'sensitive-keyboard-ready' | Out-Null
    $sensitiveWindow = Capture-Window 'sensitive-suppression' -Screenshot
    $sensitiveDocument = Read-Window $sensitiveWindow
    foreach ($forbidden in @('Emoji', 'Emoji search', "$searchEmoji emoji", "$variantEmoji emoji")) {
        if ($null -ne (Find-Node $sensitiveDocument @($forbidden))) {
            throw "Sensitive editor exposed forbidden emoji surface state '$forbidden'."
        }
    }

    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Finish -Serial $Serial -PackageName $sellerPackage `
        -OutputDirectory $attributionRoot `
        -Canary @($searchQuery, $searchEmoji, $variantEmoji) -NetworkExpectation Zero
    $attributionFinished = $true
    $attributionSummary = Get-Content -Raw -LiteralPath `
        (Join-Path $attributionRoot 'summary.json') | ConvertFrom-Json
} finally {
    if ($fixtureCaptured) {
        Start-HostEditor 'fixture-restore' | Out-Null
        $restoreBundle = Call-Bridge $imeBridge 'restore_emoji_recents_fixture'
        Assert-Value (Bundle-Value $restoreBundle 'emoji_recents_fixture_restored') `
            'true' 'Emoji fixture restore'
        Assert-Value (Bundle-Value $restoreBundle 'emoji_recents_sha256') `
            (Bundle-Value $captureBundle 'emoji_recents_original_sha256') `
            'Exact ordered emoji recents restore'
        Assert-Value (Bundle-Value $restoreBundle 'emoji_recents_count') `
            (Bundle-Value $captureBundle 'emoji_recents_original_count') `
            'Original emoji recents count restore'
        Assert-Value (Bundle-Value $restoreBundle 'emoji_variant_present') `
            (Bundle-Value $captureBundle 'emoji_variant_original_present') `
            'Original Emoji2 variant preference presence restore'
        Assert-Value (Bundle-Value $restoreBundle 'emoji_variant_sha256') `
            (Bundle-Value $captureBundle 'emoji_variant_original_sha256') `
            'Exact original Emoji2 variant preference restore'
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

if (-not $attributionFinished) { throw 'UID attribution did not finish.' }

[ordered]@{
    overall = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    serial = $Serial
    api = $deviceApi
    realInstalledImeOpenVerified = $true
    touchOnlySearchVerified = $true
    searchCommitExact = $true
    categoryNavigationVerified = $true
    longPressVariantPopupVerified = $true
    variantCommitExact = $true
    recentsPersistenceAfterProcessRecreationVerified = $true
    productUiClearVerified = $true
    clearPersistenceAfterProcessRecreationVerified = $true
    sensitiveFieldSuppressionVerified = $true
    semanticAlphabeticKeyCount = $semanticKeyCount
    searchFieldHeightDp = [math]::Round($searchFieldHeightDp, 2)
    searchKeyHeightDp = [math]::Round($keyHeightDp, 2)
    searchResultHeightDp = [math]::Round($searchResultHeightDp, 2)
    clearActionHeightDp = [math]::Round($clearHeightDp, 2)
    originalRecentsRestoredExactly = $true
    originalVariantSelectionRestoredExactly = $true
    originalRecentsCount = [int](Bundle-Value $captureBundle 'emoji_recents_original_count')
    networkAccountingSupported = [bool]$attributionSummary.networkAccountingSupported
    uidRxDelta = $attributionSummary.rxDelta
    uidTxDelta = $attributionSummary.txDelta
    controlledValueOccurrencesInUidLog = `
        [int]$attributionSummary.canaryOccurrencesInUidLog
    artifacts = [ordered]@{
        openUi = Join-Path $OutputDirectory 'emoji-open.png'
        touchSearchKeypadUi = Join-Path $OutputDirectory 'touch-search-keypad.png'
        searchResultUi = Join-Path $OutputDirectory 'search-result.png'
        persistedRecentUi = Join-Path $OutputDirectory 'recent-persisted.png'
        variantPopupUi = Join-Path $OutputDirectory 'variant-popup.png'
        clearedUi = Join-Path $OutputDirectory 'recents-cleared.png'
        clearPersistedUi = Join-Path $OutputDirectory 'recents-clear-persisted.png'
        sensitiveUi = Join-Path $OutputDirectory 'sensitive-suppression.png'
        attributionSummary = Join-Path $attributionRoot 'summary.json'
    }
} | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 `
    (Join-Path $OutputDirectory 'summary.json')

Write-Host "Emoji emulator evidence passed: $OutputDirectory"
