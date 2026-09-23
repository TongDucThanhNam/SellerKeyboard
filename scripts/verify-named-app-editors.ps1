[CmdletBinding()]
param(
    [string]$Serial = "",
    [string]$OutputDirectory = ""
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
} else {
    "adb"
}
$sellerPackage = "com.terasumi.sellerkeyboard"
$sellerIme = "$sellerPackage/.service.SellerKeyboard"
$chromePackage = "com.android.chrome"
$chromeActivity = "$chromePackage/com.google.android.apps.chrome.Main"
$messagesPackage = "com.google.android.apps.messaging"
$messagesActivity = "$messagesPackage/.ui.ConversationListActivity"
$canary = "skqvz"
$privacyCanaries = @($canary, 'Skqvz', '5550100')
$attributionScript = Join-Path $PSScriptRoot 'measure-uid-attribution.ps1'

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: adb $($Arguments -join ' ')`n$($result -join "`n")"
    }
    $result
}

function Get-Setting([string]$Namespace, [string]$Name) {
    ((Adb '-s' $Serial shell settings get $Namespace $Name) -join "").Trim()
}

function Restore-Setting([string]$Namespace, [string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -eq "null") {
        Adb '-s' $Serial shell settings delete $Namespace $Name | Out-Null
    } else {
        Adb '-s' $Serial shell settings put $Namespace $Name $Value | Out-Null
    }
}

function Dump-Window([string]$Name) {
    $remote = "/sdcard/named-app-$Name.xml"
    $local = Join-Path $OutputDirectory "$Name-window.xml"
    Adb '-s' $Serial shell uiautomator dump '--windows' $remote | Out-Null
    Adb '-s' $Serial pull '-q' $remote $local | Out-Null
    Get-Content -Raw -Encoding UTF8 -LiteralPath $local
}

function Capture-Screen([string]$Name) {
    $remote = "/sdcard/named-app-$Name.png"
    $local = Join-Path $OutputDirectory "$Name.png"
    Adb '-s' $Serial shell screencap '-p' $remote | Out-Null
    Adb '-s' $Serial pull '-q' $remote $local | Out-Null
}

function Find-NodeByValue([string]$Xml, [string]$Value) {
    $escaped = [regex]::Escape($Value)
    foreach ($node in [regex]::Matches($Xml, '<node[^>]*/?>')) {
        if ($node.Value -match "(?:text|content-desc|hint)=`"$escaped`"") {
            return $node
        }
    }
    $null
}

function Find-NodeByResourceId([string]$Xml, [string]$ResourceId) {
    $escaped = [regex]::Escape($ResourceId)
    foreach ($node in [regex]::Matches($Xml, '<node[^>]*/?>')) {
        if ($node.Value -match "resource-id=`"$escaped`"") {
            return $node
        }
    }
    $null
}

function Node-Center([System.Text.RegularExpressions.Match]$Node) {
    $bounds = [regex]::Match(
        $Node.Value,
        'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    )
    if (-not $bounds.Success) {
        throw "Node has no bounds: $($Node.Value)"
    }
    [pscustomobject]@{
        X = [int](([int]$bounds.Groups[1].Value + [int]$bounds.Groups[3].Value) / 2)
        Y = [int](([int]$bounds.Groups[2].Value + [int]$bounds.Groups[4].Value) / 2)
    }
}

function Tap-Node([System.Text.RegularExpressions.Match]$Node) {
    $point = Node-Center $Node
    Adb '-s' $Serial shell input tap $point.X $point.Y | Out-Null
}

function Tap-OptionalValue([string]$Xml, [string]$Value) {
    $node = Find-NodeByValue $Xml $Value
    if ($null -eq $node) { return $false }
    Tap-Node $node
    Start-Sleep -Seconds 2
    $true
}

function Clear-FocusedField {
    Adb '-s' $Serial shell input keycombination '-t' 100 `
        KEYCODE_CTRL_LEFT KEYCODE_A | Out-Null
    Adb '-s' $Serial shell input keyevent KEYCODE_DEL | Out-Null
    Start-Sleep -Milliseconds 300
}

function Type-TextWithIme([string]$Xml, [string]$Text) {
    $keyNodes = @([regex]::Matches(
        $Xml,
        '<node[^>]*content-desc="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*/?>'
    ))
    foreach ($character in $Text.ToCharArray()) {
        $pattern = '(?i)^' + [regex]::Escape([string]$character) + '$'
        $key = $keyNodes | Where-Object { $_.Groups[1].Value -match $pattern } |
            Select-Object -First 1
        if ($null -eq $key) {
            throw "SellerKeyboard key '$character' was absent from the UI tree"
        }
        $x = [int](([int]$key.Groups[2].Value + [int]$key.Groups[4].Value) / 2)
        $y = [int](([int]$key.Groups[3].Value + [int]$key.Groups[5].Value) / 2)
        Adb '-s' $Serial shell input tap $x $y | Out-Null
        Start-Sleep -Milliseconds 180
    }
    $keyNodes.Count
}

function Assert-ImeSession([string]$TargetPackage) {
    $inputMethod = (Adb '-s' $Serial shell dumpsys input_method) -join "`n"
    if ($inputMethod -notmatch [regex]::Escape("mCurId=$sellerIme") -or
        $inputMethod -notmatch 'mInputShown=true') {
        throw "SellerKeyboard was not visible for $TargetPackage"
    }
    $windows = (Adb '-s' $Serial shell dumpsys window windows) -join "`n"
    if ($windows -notmatch 'imeInputTarget.*' + [regex]::Escape($TargetPackage)) {
        throw "IME input target was not $TargetPackage"
    }
    [ordered]@{
        selectedIme = $sellerIme
        inputShown = $true
        targetPackage = $TargetPackage
    }
}

function Package-Version([string]$PackageName) {
    $dump = (Adb '-s' $Serial shell dumpsys package $PackageName) -join "`n"
    [ordered]@{
        versionName = [regex]::Match($dump, 'versionName=([^\r\n]+)').Groups[1].Value.Trim()
        versionCode = [regex]::Match($dump, 'versionCode=(\d+)').Groups[1].Value
    }
}

$devices = @(Adb devices | Where-Object { $_ -match "\tdevice$" })
if ([string]::IsNullOrWhiteSpace($Serial)) {
    if ($devices.Count -ne 1) {
        throw "Specify -Serial when exactly one authorized device is not connected"
    }
    $Serial = ($devices[0] -split "\t")[0]
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repositoryRoot `
        "docs\evidence\verification\emulator-named-apps\latest"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$original = [ordered]@{
    defaultIme = Get-Setting secure default_input_method
    showImeWithHardKeyboard = Get-Setting secure show_ime_with_hard_keyboard
}
$original | ConvertTo-Json | Set-Content -Encoding utf8 `
    (Join-Path $OutputDirectory 'original-state.json')

try {
    foreach ($packageName in @($sellerPackage, $chromePackage, $messagesPackage)) {
        $path = ((Adb '-s' $Serial shell pm path $packageName) -join "").Trim()
        if ($path -notmatch '^package:') { throw "$packageName is not installed" }
    }
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Adb '-s' $Serial shell ime enable $sellerIme | Out-Null
    Adb '-s' $Serial shell ime set $sellerIme | Out-Null
    $privacyRoot = Join-Path $OutputDirectory 'privacy-attribution'
    & $attributionScript -Phase Start -Serial $Serial -PackageName $sellerPackage `
        -OutputDirectory $privacyRoot -NetworkExpectation Zero
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not start exact-UID attribution for the named-app flow'
    }

    Adb '-s' $Serial shell am force-stop $chromePackage | Out-Null
    Adb '-s' $Serial shell am start '-W' '-n' $chromeActivity | Out-Null
    Start-Sleep -Seconds 3
    $chromeXml = Dump-Window 'chrome-start'
    $chromeOnboardingStep = 0
    foreach ($label in @('Use without an account', 'Got it', 'No thanks')) {
        if (Tap-OptionalValue $chromeXml $label) {
            $chromeOnboardingStep++
            $chromeXml = Dump-Window "chrome-onboarding-$chromeOnboardingStep"
        }
    }
    $chromeField = Find-NodeByValue $chromeXml 'Search or type URL'
    if ($null -eq $chromeField) { throw "Chrome URL/search editor was not available" }
    Tap-Node $chromeField
    Start-Sleep -Seconds 2
    Clear-FocusedField
    $chromeImeXml = Dump-Window 'chrome-ime'
    $chromeSession = Assert-ImeSession $chromePackage
    $chromeSemanticNodeCount = Type-TextWithIme $chromeImeXml $canary
    Start-Sleep -Seconds 1
    $chromeResultXml = Dump-Window 'chrome-canary'
    $chromeUrl = Find-NodeByResourceId $chromeResultXml 'com.android.chrome:id/url_bar'
    if ($null -eq $chromeUrl -or $chromeUrl.Value -notmatch "text=`"$canary`"") {
        throw "Chrome URL bar did not contain the exact canary '$canary'"
    }
    Capture-Screen 'chrome-canary'
    Clear-FocusedField
    Adb '-s' $Serial shell input keyevent KEYCODE_BACK | Out-Null

    Adb '-s' $Serial shell am force-stop $messagesPackage | Out-Null
    Adb '-s' $Serial shell am start '-W' '-n' $messagesActivity | Out-Null
    Start-Sleep -Seconds 3
    $messagesXml = Dump-Window 'messages-start'
    if (Tap-OptionalValue $messagesXml 'Use Messages without an account') {
        $messagesXml = Dump-Window 'messages-account-free'
    }
    $messagesSearch = Find-NodeByValue $messagesXml 'Search messages'
    if ($null -eq $messagesSearch) { throw "Messages search control was not available" }
    Tap-Node $messagesSearch
    Start-Sleep -Seconds 2
    $messagesXml = Dump-Window 'messages-search-open'
    $messagesEditor = Find-NodeByResourceId $messagesXml `
        'com.google.android.apps.messaging:id/zero_state_search_box_auto_complete'
    if ($null -eq $messagesEditor) {
        $messagesEditor = Find-NodeByValue $messagesXml 'Search messages'
    }
    if ($null -eq $messagesEditor) { throw "Messages search editor was not available" }
    Tap-Node $messagesEditor
    Start-Sleep -Seconds 2
    Clear-FocusedField
    $messagesImeXml = Dump-Window 'messages-ime'
    $messagesSession = Assert-ImeSession $messagesPackage
    $messagesSemanticNodeCount = Type-TextWithIme $messagesImeXml $canary
    Start-Sleep -Seconds 1
    $messagesResultXml = Dump-Window 'messages-canary'
    $messagesDraft = Find-NodeByResourceId $messagesResultXml `
        'com.google.android.apps.messaging:id/zero_state_search_box_auto_complete'
    if ($null -eq $messagesDraft -or
        $messagesDraft.Value -notmatch "text=`"$canary`"") {
        throw "Messages search editor did not contain the exact canary '$canary'"
    }
    Capture-Screen 'messages-canary'
    Clear-FocusedField
    Adb '-s' $Serial shell input keyevent KEYCODE_BACK | Out-Null

    # Exercise a real SMS composer with an Android-reserved fictional number.
    # The script never invokes either Messages' send control or the IME Send key.
    $fictionalRecipient = '5550100'
    $formattedRecipient = '555-0100'
    $composeExpected = $canary.Substring(0, 1).ToUpperInvariant() + $canary.Substring(1)
    Adb '-s' $Serial shell am force-stop $messagesPackage | Out-Null
    Adb '-s' $Serial shell am start '-W' '-n' $messagesActivity | Out-Null
    Start-Sleep -Seconds 3
    $messagesHomeXml = Dump-Window 'messages-compose-home'
    $startChat = Find-NodeByValue $messagesHomeXml 'Start chat'
    if ($null -eq $startChat) { throw "Messages Start chat control was not available" }
    Tap-Node $startChat
    Start-Sleep -Seconds 2
    $newConversationXml = Dump-Window 'messages-new-conversation'
    $recipientPrompt = Find-NodeByValue $newConversationXml `
        'Type names, phone numbers, or emails'
    if ($null -eq $recipientPrompt) { throw "Messages recipient editor was not available" }
    Tap-Node $recipientPrompt
    Start-Sleep -Seconds 2
    $recipientImeXml = Dump-Window 'messages-recipient-ime'
    $recipientSession = Assert-ImeSession $messagesPackage
    $recipientSemanticNodeCount = Type-TextWithIme $recipientImeXml $fictionalRecipient
    Start-Sleep -Seconds 1
    $recipientResultXml = Dump-Window 'messages-recipient-result'
    $recipientEditor = Find-NodeByResourceId $recipientResultXml 'ContactSearchField'
    if ($null -eq $recipientEditor -or
        $recipientEditor.Value -notmatch "text=`"$fictionalRecipient`"") {
        throw "Messages recipient editor did not contain fictional recipient $fictionalRecipient"
    }
    $sendToRecipient = Find-NodeByValue $recipientResultXml "Send to $formattedRecipient"
    if ($null -eq $sendToRecipient) { throw "Messages did not resolve the fictional recipient" }
    Tap-Node $sendToRecipient
    Start-Sleep -Seconds 4
    $composerXml = Dump-Window 'messages-composer'
    $composer = Find-NodeByResourceId $composerXml `
        'com.google.android.apps.messaging:id/compose_message_text'
    if ($null -eq $composer) { throw "Messages SMS compose editor was not available" }
    Tap-Node $composer
    Start-Sleep -Seconds 2
    Clear-FocusedField
    $composerImeXml = Dump-Window 'messages-composer-ime'
    $composerSession = Assert-ImeSession $messagesPackage
    $composerSemanticNodeCount = Type-TextWithIme $composerImeXml $canary
    Start-Sleep -Seconds 1
    $composerResultXml = Dump-Window 'messages-compose-canary'
    $composerDraft = Find-NodeByResourceId $composerResultXml `
        'com.google.android.apps.messaging:id/compose_message_text'
    $sendSms = Find-NodeByValue $composerResultXml 'Send SMS'
    if ($null -eq $composerDraft -or
        $composerDraft.Value -notmatch "text=`"$composeExpected`"" -or
        $null -eq $sendSms) {
        throw "Messages SMS composer did not retain the exact unsent draft '$composeExpected'"
    }
    Capture-Screen 'messages-compose-canary'

    & $attributionScript -Phase Finish -Serial $Serial -PackageName $sellerPackage `
        -OutputDirectory $privacyRoot -NetworkExpectation Zero -Canary $privacyCanaries
    if ($LASTEXITCODE -ne 0) {
        throw 'Named-app exact-UID attribution failed'
    }
    $privacySummaryPath = Join-Path $privacyRoot 'summary.json'
    if (-not (Test-Path -LiteralPath $privacySummaryPath)) {
        throw "Named-app attribution summary is missing: $privacySummaryPath"
    }
    $privacyAttribution = Get-Content -Raw -LiteralPath $privacySummaryPath |
        ConvertFrom-Json
    if (-not $privacyAttribution.networkAccountingSupported -or
        [long]$privacyAttribution.rxDelta -ne 0 -or
        [long]$privacyAttribution.txDelta -ne 0 -or
        [int]$privacyAttribution.canaryOccurrencesInUidLog -ne 0) {
        throw 'Named-app exact-UID privacy thresholds failed'
    }

    $fatalLogs = @(
        Adb '-s' $Serial logcat '-d' '-v' 'brief' |
            Select-String -Pattern 'FATAL EXCEPTION|Process: com\.terasumi\.sellerkeyboard'
    )
    if ($fatalLogs.Count -ne 0) {
        throw "SellerKeyboard emitted fatal logs during named-app verification"
    }

    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        api = [int](((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join "").Trim())
        canary = $canary
        submitted = $false
        accountUsed = $false
        chrome = [ordered]@{
            requiredByGoal = $true
            package = $chromePackage
            version = Package-Version $chromePackage
            editorClass = 'URL/search'
            exactDraftVerified = $true
            imeKeyTapCount = $canary.Length
            sellerSemanticNodeCount = $chromeSemanticNodeCount
            imeSession = $chromeSession
        }
        messages = [ordered]@{
            productConfirmationPending = $true
            package = $messagesPackage
            version = Package-Version $messagesPackage
            editorClass = 'search + SMS compose'
            exactDraftVerified = $true
            imeKeyTapCount = $canary.Length * 2 + $fictionalRecipient.Length
            sellerSemanticNodeCount = $messagesSemanticNodeCount
            imeSession = $messagesSession
            search = [ordered]@{
                expectedDraft = $canary
                exactDraftVerified = $true
                imeKeyTapCount = $canary.Length
                imeSession = $messagesSession
            }
            compose = [ordered]@{
                fictionalRecipient = $fictionalRecipient
                formattedRecipient = $formattedRecipient
                expectedDraft = $composeExpected
                exactDraftVerified = $true
                submitted = $false
                recipientImeKeyTapCount = $fictionalRecipient.Length
                draftImeKeyTapCount = $canary.Length
                recipientSemanticNodeCount = $recipientSemanticNodeCount
                composerSemanticNodeCount = $composerSemanticNodeCount
                recipientImeSession = $recipientSession
                composerImeSession = $composerSession
                sendControlPresent = $true
                imeActionLabel = 'Send'
            }
        }
        privacy = [ordered]@{
            namedTargetsVerified = $true
            targetPackages = @($chromePackage, $messagesPackage)
            attributionPackage = $privacyAttribution.packageName
            attributionUid = [int]$privacyAttribution.uid
            networkExpectation = $privacyAttribution.networkExpectation
            networkAccountingSupported = [bool]$privacyAttribution.networkAccountingSupported
            rxDelta = [long]$privacyAttribution.rxDelta
            txDelta = [long]$privacyAttribution.txDelta
            canaries = @($privacyAttribution.canaries)
            canaryOccurrencesInUidLog = `
                [int]$privacyAttribution.canaryOccurrencesInUidLog
            attributionSummary = 'privacy-attribution/summary.json'
            uidLog = 'privacy-attribution/uid-logcat.txt'
            uidNetstats = 'privacy-attribution/uid-netstats-lines.txt'
        }
        sellerFatalLogCount = 0
        claimBoundary = 'Account-free Chrome URL/search plus Google Messages search and an unsent SMS compose draft to Android-reserved fictional number 555-0100. Exact SellerKeyboard-UID attribution records zero RX/TX delta and zero controlled-text log occurrences across both named targets. No query or message was submitted. Product-confirmed commerce/messaging selection remains pending.'
    } | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'summary.json')
    Write-Host "Named-app editor verification passed: $OutputDirectory"
} finally {
    # Remove controlled drafts without submitting them and leave no target app running.
    try { Clear-FocusedField } catch { }
    try { Adb '-s' $Serial shell input keyevent KEYCODE_BACK | Out-Null } catch { }
    try { Adb '-s' $Serial shell am force-stop $chromePackage | Out-Null } catch { }
    try { Adb '-s' $Serial shell am force-stop $messagesPackage | Out-Null } catch { }
    Restore-Setting secure show_ime_with_hard_keyboard $original.showImeWithHardKeyboard
    if (-not [string]::IsNullOrWhiteSpace($original.defaultIme) -and
        $original.defaultIme -ne 'null') {
        Adb '-s' $Serial shell ime set $original.defaultIme | Out-Null
    }
}
