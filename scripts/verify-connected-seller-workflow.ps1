[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = "Stop"
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" } else { "adb" }
$sellerPackage = "com.terasumi.sellerkeyboard"
$hostPackage = "com.terasumi.sellerkeyboard.benchmarkhost"
$ime = "$sellerPackage/.service.SellerKeyboard"
$hostActivity = "$hostPackage/.ImeBenchmarkActivity"
$bridge = "content://$sellerPackage.ime-test-bridge"
$viResultThree = "K$([char]0x1EBF)t qu$([char]0x1EA3): 3"
$viSendResult = "G$([char]0x1EED)i k$([char]0x1EBF)t qu$([char]0x1EA3)"
$viCalculator = "M$([char]0x00E1)y t$([char]0x00ED)nh"
$sellerDiscountText = "80.000$([char]0x20AB)"
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

function Await-BridgeValue(
    [string]$Method,
    [string]$Key,
    [string]$Expected,
    [int]$Seconds = 12
) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $bundle = Call-Bridge $Method
        if ((Bundle-Value $bundle $Key) -eq $Expected) { return $bundle }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for bridge $Method $Key=$Expected. Last: $bundle"
}

function Capture-Window([string]$Name, [switch]$Screenshot) {
    $remoteXml = "/sdcard/seller-workflow-$Name.xml"
    $localXml = Join-Path $OutputDirectory "$Name-window.xml"
    Adb '-s' $Serial shell uiautomator dump '--windows' $remoteXml | Out-Null
    Adb '-s' $Serial pull '-q' $remoteXml $localXml | Out-Null
    if ($Screenshot) {
        $remotePng = "/sdcard/seller-workflow-$Name.png"
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

function Await-Node([string[]]$Values, [string]$Name, [int]$Seconds = 12) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $local = Capture-Window $Name
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $local
        $node = Find-Node $xml $Values
        if ($null -ne $node) {
            return [pscustomobject]@{ Node = $node; Xml = $xml; Path = $local }
        }
        Start-Sleep -Milliseconds 150
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for '$($Values -join "' or '")' in $local"
}

function Tap-Node([string[]]$Values, [string]$Name) {
    $found = Await-Node $Values $Name
    Invoke-NodeTap $found.Node
    Start-Sleep -Milliseconds 400
    $found.Path
}

function Await-NodeOrder(
    [string[]]$FirstValues,
    [string[]]$SecondValues,
    [string]$Name,
    [int]$Seconds = 12
) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $local = Capture-Window $Name
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $local
        $first = Find-Node $xml $FirstValues
        $second = Find-Node $xml $SecondValues
        if ($null -ne $first -and $null -ne $second -and
            [int]$first.Groups[2].Value -lt [int]$second.Groups[2].Value) {
            return [pscustomobject]@{ Path = $local; Xml = $xml }
        }
        Start-Sleep -Milliseconds 150
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for '$($FirstValues -join "' or '")' above " +
        "'$($SecondValues -join "' or '")' in $local"
}

function Assert-NodeAbsent([string]$Xml, [string[]]$Values, [string]$Context) {
    if ($null -ne (Find-Node $Xml $Values)) {
        throw "Unexpected '$($Values -join "' or '")' remained visible in $Context"
    }
}

function Start-HostEditor([string]$Name) {
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $hostActivity | Out-Null
    $editor = Await-Node @('IME benchmark editor') "$Name-editor"
    Invoke-NodeTap $editor.Node
    Start-Sleep -Milliseconds 500
    $deadline = (Get-Date).AddSeconds(12)
    do {
        $snapshot = Call-Bridge 'snapshot'
        if ((Bundle-Value $snapshot 'active') -eq 'true' -and
            (Bundle-Value $snapshot 'editor_package') -eq $hostPackage -and
            (Bundle-Value $snapshot 'sensitive') -eq 'false') {
            return $snapshot
        }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for the neutral host IME session. Last: $snapshot"
}

function Open-Replies([string]$Name) {
    Tap-Node @('Snippet List', 'Snippets') "$Name-replies" | Out-Null
    Await-Node @('Search seller replies and commands') "$Name-replies-open" | Out-Null
}

function Await-EditorText([string]$Expected, [string]$Name) {
    $deadline = (Get-Date).AddSeconds(12)
    do {
        $local = Capture-Window $Name
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $local
        $match = [regex]::Match(
            $xml,
            '<node[^>]*text="' + [regex]::Escape($Expected) + '"[^>]*package="' +
                [regex]::Escape($hostPackage) + '"[^>]*/?>'
        )
        if ($match.Success) { return $local }
        Start-Sleep -Milliseconds 150
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for neutral editor text '$Expected'. Last XML: $local"
}

function Assert-DisabledInsert([string]$Name) {
    $found = Await-Node @('Insert reviewed reply') $Name
    [xml]$hierarchy = $found.Xml
    $actionNode = $hierarchy.SelectSingleNode(
        "//node[@content-desc='Insert reviewed reply' or @text='Insert reviewed reply']"
    )
    $actionParent = if ($actionNode) { $actionNode.ParentNode } else { $null }
    if (-not $actionParent -or $actionParent.clickable -ne 'true' -or
        $actionParent.enabled -ne 'false') {
        throw "Unresolved template insert remained clickable: $($found.Node.Value)"
    }
    $found.Path
}

function Assert-EnabledInsert([string]$Name) {
    $found = Await-Node @('Insert reviewed reply') $Name
    [xml]$hierarchy = $found.Xml
    $actionNode = $hierarchy.SelectSingleNode(
        "//node[@content-desc='Insert reviewed reply' or @text='Insert reviewed reply']"
    )
    $actionParent = if ($actionNode) { $actionNode.ParentNode } else { $null }
    if (-not $actionParent -or $actionParent.clickable -ne 'true' -or
        $actionParent.enabled -ne 'true') {
        throw "Resolved template did not enable its parent insert action: $($found.Node.Value)"
    }
    $found.Path
}

function Enter-InImeText([string]$Value, [string]$Name) {
    $local = Capture-Window "$Name-keypad"
    $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $local
    $numericMode = $false
    foreach ($character in $Value.ToCharArray()) {
        $description = if ($character -eq ' ') {
            'Text entry space'
        } else {
            "Text entry key $character"
        }
        $node = Find-Node $xml @($description)
        if ($null -eq $node -and [char]::IsDigit($character) -and -not $numericMode) {
            $mode = Find-Node $xml @('Show numeric text entry')
            if ($null -eq $mode) { throw "Keypad cannot switch to numeric entry in $local" }
            Invoke-NodeTap $mode
            Start-Sleep -Milliseconds 300
            $local = Capture-Window "$Name-numeric-keypad"
            $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $local
            $numericMode = $true
            $node = Find-Node $xml @($description)
        } elseif ($null -eq $node -and [char]::IsLetter($character) -and $numericMode) {
            $mode = Find-Node $xml @('Show alphabetic text entry')
            if ($null -eq $mode) { throw "Keypad cannot switch to alphabetic entry in $local" }
            Invoke-NodeTap $mode
            Start-Sleep -Milliseconds 300
            $local = Capture-Window "$Name-alphabetic-keypad"
            $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $local
            $numericMode = $false
            $node = Find-Node $xml @($description)
        }
        if ($null -eq $node) { throw "Keypad is missing '$description' in $local" }
        Invoke-NodeTap $node
        Start-Sleep -Milliseconds 120
    }
    $done = Find-Node $xml @('Finish text entry')
    if ($null -eq $done) { throw "Keypad is missing its Done action in $local" }
    Invoke-NodeTap $done
    Start-Sleep -Milliseconds 500
}

$originalShowImeWithHardKeyboard = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
$seeded = $false
try {
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Adb '-s' $Serial shell ime enable $ime | Out-Null
    Adb '-s' $Serial shell ime set $ime | Out-Null

    $attributionRoot = Join-Path $OutputDirectory 'network-attribution'
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Start -Serial $Serial -PackageName $sellerPackage `
        -OutputDirectory $attributionRoot

    $initialSession = Start-HostEditor 'initial'
    $seed = Call-Bridge 'seed_seller_workflow'
    if ((Bundle-Value $seed 'seller_workflow_seeded') -ne 'true' -or
        (Bundle-Value $seed 'seller_workflow_template_count') -ne '3') {
        throw "Seller workflow seed failed: $seed"
    }
    $seeded = $true

    Open-Replies 'plain'
    # Existing user/benchmark rows are preserved by this reversible fixture and
    # can push the three controlled rows outside the visible lazy-list window.
    # Scope ordering assertions to the controlled rows without deleting or
    # mutating unrelated data.
    Tap-Node @('Search seller replies and commands') 'controlled-order-search' | Out-Null
    Enter-InImeText 'connected' 'controlled-order-search'
    $smartOrder = Await-NodeOrder @('Reply Connected Shipping Reply') `
        @('Reply Connected Warranty Reply') 'favorite-smart-order'
    $favoriteRemoved = Tap-Node `
        @('Remove favorite for Connected Shipping Reply') 'favorite-remove'
    Await-Node @('Add favorite for Connected Shipping Reply') `
        'favorite-removed-local' | Out-Null
    Await-BridgeValue 'seller_workflow_snapshot' 'connected_shipping_favorite' 'false' | `
        Out-Null
    Tap-Node @('Category') 'category-sort' | Out-Null
    Await-Node @('Add favorite for Connected Shipping Reply') `
        'favorite-removed-persisted' | Out-Null
    $categoryOrder = Await-NodeOrder @('Reply Connected Tracking Reply') `
        @('Reply Connected Warranty Reply') 'category-order'
    Tap-Node @('Add favorite for Connected Shipping Reply') 'favorite-add' | Out-Null
    Await-BridgeValue 'seller_workflow_snapshot' 'connected_shipping_favorite' 'true' | `
        Out-Null
    Tap-Node @('Recent') 'recent-sort' | Out-Null
    Await-Node @('Remove favorite for Connected Shipping Reply') `
        'favorite-restored-persisted' | Out-Null
    $recentOrder = Await-NodeOrder @('Reply Connected Warranty Reply') `
        @('Reply Connected Shipping Reply') 'recent-order'
    Tap-Node @('Reply Connected Shipping Reply') 'plain-template' | Out-Null
    Await-Node @('Your order ships today.') 'plain-review' | Out-Null
    Tap-Node @('Insert reviewed reply') 'plain-insert' | Out-Null
    $plainEditor = Await-EditorText 'Your order ships today.' 'plain-result'
    Capture-Window 'plain-passed' -Screenshot | Out-Null

    $parameterSession = Start-HostEditor 'parameter'
    Open-Replies 'parameter'
    Tap-Node @('Search seller replies and commands') 'shortcut-search' | Out-Null
    Enter-InImeText '/track-connected' 'shortcut-search'
    $shortcutResult = Await-Node @('Reply Connected Tracking Reply') `
        'shortcut-result'
    Assert-NodeAbsent $shortcutResult.Xml @('Reply Connected Shipping Reply') `
        $shortcutResult.Path
    Assert-NodeAbsent $shortcutResult.Xml @('Reply Connected Warranty Reply') `
        $shortcutResult.Path
    Tap-Node @('Reply Connected Tracking Reply') 'parameter-template' | Out-Null
    Await-Node @('Resolve every required variable before insertion.') `
        'parameter-warning' | Out-Null
    $disabledInsert = Assert-DisabledInsert 'parameter-disabled'
    Capture-Window 'parameter-unresolved' -Screenshot | Out-Null
    Tap-Node @('Template variable tracking_code') 'parameter-variable' | Out-Null
    Enter-InImeText 'trk123' 'parameter-variable'
    $enabledInsert = Assert-EnabledInsert 'parameter-enabled'
    Tap-Node @('Insert reviewed reply') 'parameter-insert' | Out-Null
    $parameterEditor = Await-EditorText 'Tracking code: trk123' 'parameter-result'
    Capture-Window 'parameter-passed' -Screenshot | Out-Null

    $commandSession = Start-HostEditor 'command'
    Open-Replies 'command'
    Tap-Node @('Search seller replies and commands') 'command-search' | Out-Null
    Enter-InImeText 'calculator' 'command-search'
    $command = Await-Node @('Seller command Calculator and seller formulas') `
        'command-result'
    Invoke-NodeTap $command.Node
    Start-Sleep -Milliseconds 500
    $calculatorSurface = Call-Bridge 'snapshot'
    if ((Bundle-Value $calculatorSurface 'active_surface') -ne 'CALCULATOR') {
        throw "Seller command did not open Calculator: $calculatorSurface"
    }
    Await-Node @('General') 'calculator-open' | Out-Null

    Tap-Node @('1') 'calculator-one' | Out-Null
    Tap-Node @('+') 'calculator-plus' | Out-Null
    Tap-Node @('2') 'calculator-two' | Out-Null
    Tap-Node @('=') 'calculator-equals' | Out-Null
    Await-Node @('Result: 3', $viResultThree) 'calculator-result' | Out-Null
    Tap-Node @('Send Result', $viSendResult) 'calculator-send' | Out-Null
    $calculatorEditor = Await-EditorText '3' 'calculator-editor-result'
    Capture-Window 'calculator-passed' -Screenshot | Out-Null

    $sellerCalculatorSession = Start-HostEditor 'seller-calculator'
    Tap-Node @('Calculator', $viCalculator) 'seller-calculator-toolbar' | Out-Null
    Tap-Node @('Seller') 'seller-calculator-mode' | Out-Null
    Tap-Node @('Seller calculator Price') 'seller-calculator-price' | Out-Null
    Enter-InImeText '100000' 'seller-calculator-price'
    Tap-Node @('Seller calculator Discount %') 'seller-calculator-discount' | Out-Null
    Enter-InImeText '20' 'seller-calculator-discount'
    Tap-Node @('Calculate') 'seller-calculator-calculate' | Out-Null
    Tap-Node @("Insert $sellerDiscountText") 'seller-calculator-insert' | Out-Null
    $sellerCalculatorEditor = Await-EditorText $sellerDiscountText 'seller-calculator-result'
    Capture-Window 'seller-calculator-passed' -Screenshot | Out-Null

    $restore = Call-Bridge 'restore_seller_workflow'
    if ((Bundle-Value $restore 'seller_workflow_restored') -ne 'true') {
        throw "Seller workflow test data was not restored: $restore"
    }
    $seeded = $false
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Finish -Serial $Serial -PackageName $sellerPackage `
        -OutputDirectory $attributionRoot -Canary @(
            'Your order ships today.',
            'Tracking code: trk123',
            'Your warranty remains active.',
            $sellerDiscountText
        )
    $attribution = Get-Content -Raw -LiteralPath `
        (Join-Path $attributionRoot 'summary.json') | ConvertFrom-Json

    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        targetPackage = $hostPackage
        targetIsDistinctPackage = $true
        templatesSeeded = 3
        favoriteSmartOrderingVerified = $true
        favoriteToggleVerified = $true
        categoryOrderingVerified = $true
        recentOrderingVerified = $true
        slashShortcutFilteringVerified = $true
        plainReplyCommitted = $true
        unresolvedTemplateCommitDisabled = $true
        resolvedTemplateCommitted = 'Tracking code: trk123'
        commandSearchOpenedCalculator = $true
        generalCalculatorCommitted = '3'
        sellerDiscountCommitted = $sellerDiscountText
        testDataRestored = $true
        networkAccountingSupported = [bool]$attribution.networkAccountingSupported
        uidRxDelta = $attribution.rxDelta
        uidTxDelta = $attribution.txDelta
        controlledTextOccurrencesInUidLog = `
            [int]$attribution.canaryOccurrencesInUidLog
        initialSession = $initialSession
        parameterSession = $parameterSession
        commandSession = $commandSession
        sellerCalculatorSession = $sellerCalculatorSession
        smartOrderWindow = $smartOrder.Path
        favoriteToggleWindow = $favoriteRemoved
        categoryOrderWindow = $categoryOrder.Path
        recentOrderWindow = $recentOrder.Path
        shortcutResultWindow = $shortcutResult.Path
        plainEditorWindow = $plainEditor
        unresolvedWindow = $disabledInsert
        resolvedWindow = $enabledInsert
        parameterEditorWindow = $parameterEditor
        calculatorEditorWindow = $calculatorEditor
        sellerCalculatorEditorWindow = $sellerCalculatorEditor
        claimBoundary = 'real emulator IME UI and neutral cross-package editor; named third-party and physical-device acceptance remain pending'
    } | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'summary.json')
    Write-Host "Connected seller workflow passed: $OutputDirectory"
} finally {
    if ($seeded) {
        $restore = Call-Bridge 'restore_seller_workflow'
        if ((Bundle-Value $restore 'seller_workflow_restored') -ne 'true') {
            throw "Seller workflow cleanup did not confirm success: $restore"
        }
    }
    if ($originalShowImeWithHardKeyboard -eq 'null' -or
        [string]::IsNullOrWhiteSpace($originalShowImeWithHardKeyboard)) {
        Adb '-s' $Serial shell settings delete secure show_ime_with_hard_keyboard | Out-Null
    } else {
        Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard `
            $originalShowImeWithHardKeyboard | Out-Null
    }
}
