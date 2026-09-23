[CmdletBinding()]
param(
    [string]$Serial = 'emulator-5554',
    [string]$OutputDirectory = (
        'docs\evidence\verification\emulator-named-password-manager\api36-keepassdx-4.4.5-v1'
    ),
    [switch]$SkipBuild,
    [switch]$IncludeBrowserFlow
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$OutputDirectory = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputDirectory))
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
} else {
    'adb'
}
$gradle = Join-Path $repositoryRoot 'gradlew.bat'

$imePackage = 'com.terasumi.sellerkeyboard'
$imeComponent = 'com.terasumi.sellerkeyboard/.service.SellerKeyboard'
$imeBridge = 'content://com.terasumi.sellerkeyboard.ime-test-bridge'
$hostPackage = 'com.terasumi.sellerkeyboard.benchmarkhost'
$hostComponent = "$hostPackage/.AutofillBenchmarkActivity"
$hostBridge = "content://$hostPackage.host-test-bridge"
$browserPackage = 'com.android.chrome'
$browserFixturePort = 18081
$browserFixturePath = '/autofill-login.html'
$browserFixtureUrl = "http://127.0.0.1:$browserFixturePort$browserFixturePath"
$browserFixtureFile = Join-Path $repositoryRoot 'benchmark-fixtures\autofill-login.html'
$browserServerLog = Join-Path $OutputDirectory 'browser-http-requests.txt'
$browserServerReady = Join-Path $OutputDirectory 'browser-http-ready.txt'
$browserServerStop = Join-Path $OutputDirectory 'browser-http-stop.txt'
$providerPackage = 'com.kunzisoft.keepass.libre'
$providerComponent = (
    "$providerPackage/com.kunzisoft.keepass.credentialprovider.autofill.KeeAutofillService"
)
$providerVersion = '4.4.5'
$providerVersionCode = 44500
$providerUrl = (
    'https://github.com/Kunzisoft/KeePassDX/releases/download/4.4.5/' +
    'KeePassDX-4.4.5-libre.apk'
)
$providerApkSha256 = 'EFCCBC7722AEB93616AF541A5A5137418D7EAC42A36DD16391948BCFAADDCF79'
$providerCertificateSha256 = (
    '7d55b8af210381aabf960f07e17cf7857b6d2a642ca2da6bf0bdf1b200362f04'
)
$vaultFileName = 'sellerkeyboard-named-autofill.kdbx'
$vaultDevicePath = "/sdcard/Download/$vaultFileName"
$masterPassword = 'NamedFixtureMaster42!'
$entryTitle = 'Seller Keyboard Host'
$browserEntryTitle = 'Seller Keyboard Browser'
$username = 'named-provider@example.invalid'
$password = 'Named-Provider-Autofill-42!'
$entryUrl = "androidapp://$hostPackage"
$browserEntryUrl = $browserFixtureUrl

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & $adb @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed ($LASTEXITCODE): $($Arguments -join ' ')"
    }
}

function Bundle-Value([string]$Text, [string]$Key) {
    $match = [regex]::Match($Text, "(?:Bundle\[\{|[, ])$([regex]::Escape($Key))=([^,\}]*)")
    if (-not $match.Success) { return $null }
    $match.Groups[1].Value
}

function Call-Bridge([string]$Uri, [string]$Method) {
    (Adb '-s' $Serial shell content call --uri $Uri --method $Method) -join "`n"
}

function Wait-BridgeValue(
    [string]$Uri,
    [string]$Method,
    [string]$Key,
    [string[]]$Expected,
    [int]$Seconds = 30
) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    $snapshot = ''
    do {
        $snapshot = Call-Bridge $Uri $Method
        if ((Bundle-Value $snapshot $Key) -in $Expected) { return $snapshot }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Key in [$($Expected -join ', ')]. Last: $snapshot"
}

function Try-Wait-BridgeValue(
    [string]$Uri,
    [string]$Method,
    [string]$Key,
    [string[]]$Expected,
    [int]$Seconds = 5
) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $snapshot = Call-Bridge $Uri $Method
        if ((Bundle-Value $snapshot $Key) -in $Expected) { return $snapshot }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    $null
}

function Sha256-Text([string]$Value) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
    -join ($hash | ForEach-Object { $_.ToString('x2') })
}

function File-Metadata([string]$Path) {
    $file = Get-Item -LiteralPath $Path
    [ordered]@{
        file = $file.Name
        bytes = [long]$file.Length
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash
    }
}

function Test-PackageInstalled([string]$PackageName) {
    $output = @(& $adb '-s' $Serial shell pm path $PackageName 2>$null)
    $exitCode = $LASTEXITCODE
    $exitCode -eq 0 -and (($output -join "`n") -match '^package:')
}

function Test-RemoteFile([string]$Path) {
    & $adb '-s' $Serial shell test '-e' $Path
    $LASTEXITCODE -eq 0
}

function Restore-Setting([string]$Namespace, [string]$Key, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -eq 'null') {
        Adb '-s' $Serial shell settings delete $Namespace $Key | Out-Null
    } else {
        Adb '-s' $Serial shell settings put $Namespace $Key $Value | Out-Null
    }
}

function Get-BoundsCenter([System.Xml.XmlElement]$Node) {
    $match = [regex]::Match($Node.bounds, '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (-not $match.Success) { throw "Invalid node bounds: $($Node.bounds)" }
    [pscustomobject]@{
        X = [int](([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2)
        Y = [int](([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2)
    }
}

function Dump-Windows([string]$Name) {
    $safeName = $Name -replace '[^A-Za-z0-9._-]', '-'
    $remote = "/sdcard/sellerkeyboard-named-autofill-$safeName.xml"
    $local = Join-Path $OutputDirectory "$safeName-window.xml"
    Adb '-s' $Serial shell uiautomator dump '--windows' $remote | Out-Null
    Adb '-s' $Serial pull '-q' $remote $local | Out-Null
    [xml](Get-Content -Raw -Encoding UTF8 -LiteralPath $local)
}

function Wait-Node([string]$XPath, [string]$Name, [int]$Seconds = 30) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try {
            $xml = Dump-Windows "wait-$Name"
            $node = $xml.SelectSingleNode($XPath)
            if ($node) { return $node }
        } catch {
            # DocumentsUI and secure activities can briefly return a null root during transitions.
        }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for UI node $Name ($XPath)"
}

function Tap-Node([string]$XPath, [string]$Name, [int]$Seconds = 30) {
    $node = Wait-Node $XPath $Name $Seconds
    $center = Get-BoundsCenter $node
    Adb '-s' $Serial shell input tap $center.X $center.Y | Out-Null
    Start-Sleep -Milliseconds 400
}

function Set-EditText([string]$Hint, [string]$Value) {
    $xpath = "//node[@class='android.widget.EditText' and @hint='$Hint']"
    Tap-Node $xpath "edit-$Hint"
    Adb '-s' $Serial shell input text ($Value.Replace(' ', '%s')) | Out-Null
    Start-Sleep -Milliseconds 500
    $node = Wait-Node $xpath "verify-$Hint"
    if ([string]$node.text -ne $Value) {
        throw "Could not set $Hint exactly. Actual: $($node.text)"
    }
}

function Dismiss-TapTargetIfPresent([string]$Name) {
    $xml = Dump-Windows "tap-target-$Name"
    $node = $xml.SelectSingleNode(
        "//node[@package='$providerPackage' and @class='android.view.View' and " +
        "@clickable='true' and @bounds='[0,0][1080,2400]']"
    )
    if ($node) {
        Adb '-s' $Serial shell input tap 540 1200 | Out-Null
        Start-Sleep -Milliseconds 400
        return $true
    }
    $false
}

function Open-NewEntry([string]$Name) {
    $menuXPath = "//node[@resource-id='$providerPackage`:id/fab_add_entry']"
    foreach ($attempt in 1..4) {
        Tap-Node "//node[@resource-id='$providerPackage`:id/add_button']" `
            "$Name-add-button-$attempt"
        $deadline = (Get-Date).AddSeconds(4)
        do {
            $xml = Dump-Windows "$Name-add-menu-$attempt"
            $entryNode = $xml.SelectSingleNode($menuXPath)
            if ($entryNode) {
                $center = Get-BoundsCenter $entryNode
                Adb '-s' $Serial shell input tap $center.X $center.Y | Out-Null
                Start-Sleep -Milliseconds 500
                return
            }
            Start-Sleep -Milliseconds 250
        } while ((Get-Date) -lt $deadline)
        [void](Dismiss-TapTargetIfPresent "$Name-add-menu-overlay-$attempt")
        Start-Sleep -Milliseconds 500
    }
    throw "KeePassDX did not open the Add entry action for $Name."
}

function Complete-EntrySave([string]$Name) {
    $saveXPath = "//node[@resource-id='$providerPackage`:id/entry_edit_validate']"
    Tap-Node $saveXPath "$Name-save"
    $deadline = (Get-Date).AddSeconds(35)
    $lastSaveRetry = Get-Date
    do {
        $activities = (Adb '-s' $Serial shell dumpsys activity activities) -join "`n"
        $groupActivity = [regex]::Escape(
            "$providerPackage/com.kunzisoft.keepass.activities.GroupActivity"
        )
        if ($activities -match "topResumedActivity=.*$groupActivity") {
            return
        }
        $xml = Dump-Windows "$Name-save-transition"
        $laterNode = $xml.SelectSingleNode(
            "//node[@resource-id='android:id/button2' and @text='Later']"
        )
        if ($laterNode) {
            $center = Get-BoundsCenter $laterNode
            Adb '-s' $Serial shell input tap $center.X $center.Y | Out-Null
            Start-Sleep -Milliseconds 600
            continue
        }
        $saveNode = $xml.SelectSingleNode($saveXPath)
        if ($saveNode -and ((Get-Date) - $lastSaveRetry).TotalSeconds -ge 3) {
            $center = Get-BoundsCenter $saveNode
            Adb '-s' $Serial shell input tap $center.X $center.Y | Out-Null
            $lastSaveRetry = Get-Date
        }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)
    throw "KeePassDX did not complete the verified save transition for $Name."
}

function Wait-Activity([string]$Pattern, [int]$Seconds = 30) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    $state = ''
    do {
        $state = (Adb '-s' $Serial shell dumpsys activity activities) -join "`n"
        if ($state -match "topResumedActivity=.*$Pattern") { return $true }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for resumed activity matching $Pattern"
}

function Capture-Window([string]$Name) {
    $remoteXml = "/sdcard/sellerkeyboard-named-autofill-$Name.xml"
    $remotePng = "/sdcard/sellerkeyboard-named-autofill-$Name.png"
    $localXml = Join-Path $OutputDirectory "$Name-window.xml"
    $localPng = Join-Path $OutputDirectory "$Name.png"
    Adb '-s' $Serial shell uiautomator dump '--windows' $remoteXml | Out-Null
    Adb '-s' $Serial pull '-q' $remoteXml $localXml | Out-Null
    Adb '-s' $Serial shell screencap '-p' $remotePng | Out-Null
    Adb '-s' $Serial pull '-q' $remotePng $localPng | Out-Null
    [pscustomobject]@{ Xml = $localXml; Screenshot = $localPng }
}

function Capture-Screenshot([string]$Name) {
    $remotePng = "/sdcard/sellerkeyboard-named-autofill-$Name.png"
    $localPng = Join-Path $OutputDirectory "$Name.png"
    Adb '-s' $Serial shell screencap '-p' $remotePng | Out-Null
    Adb '-s' $Serial pull '-q' $remotePng $localPng | Out-Null
    $localPng
}

function Get-InlineTap([string]$XmlPath, [double]$HorizontalFraction) {
    $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $XmlPath
    $numberRowNodes = @([regex]::Matches(
        $xml,
        '<node[^>]*(?:text|content-desc)="1"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*/?>'
    ) | Where-Object {
        [int]$_.Groups[3].Value - [int]$_.Groups[1].Value -ge 60
    })
    if ($numberRowNodes.Count -eq 0) {
        throw "Could not locate the SellerKeyboard number row in $XmlPath"
    }
    $numberRowTop = @($numberRowNodes | ForEach-Object {
        [int]$_.Groups[2].Value
    } | Sort-Object | Select-Object -First 1)[0]
    $displaySize = ((Adb '-s' $Serial shell wm size) -join "`n")
    $size = [regex]::Match($displaySize, '(\d+)x(\d+)')
    if (-not $size.Success) { throw "Could not parse display size: $displaySize" }
    [pscustomobject]@{
        X = [int]([int]$size.Groups[1].Value * $HorizontalFraction)
        Y = $numberRowTop - 80
        NumberRowTop = $numberRowTop
    }
}

function Start-Attribution(
    [string]$PackageName,
    [string]$Directory,
    [ValidateSet('Zero', 'Record')][string]$NetworkExpectation = 'Zero'
) {
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Start -Serial $Serial -PackageName $PackageName `
        -OutputDirectory $Directory -NetworkExpectation $NetworkExpectation
    if ($LASTEXITCODE -ne 0) { throw "Could not start attribution for $PackageName" }
}

function Finish-Attribution(
    [string]$PackageName,
    [string]$Directory,
    [ValidateSet('Zero', 'Record')][string]$NetworkExpectation = 'Zero'
) {
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Finish -Serial $Serial -PackageName $PackageName `
        -OutputDirectory $Directory -NetworkExpectation $NetworkExpectation `
        -Canary @($entryTitle, $browserEntryTitle, $username, $password)
    if ($LASTEXITCODE -ne 0) { throw "Could not finish attribution for $PackageName" }
    Get-Content -Raw -LiteralPath (Join-Path $Directory 'summary.json') | ConvertFrom-Json
}

function Wait-BrowserStatus([string]$ExpectedStatus, [int]$Seconds = 20) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try {
            $xml = Dump-Windows 'wait-browser-status'
            $node = $xml.SelectSingleNode(
                "//node[@package='$browserPackage' and " +
                "(@text='$ExpectedStatus' or @content-desc='$ExpectedStatus')]"
            )
            if ($node) { return $xml }
        } catch {
            # Chrome's accessibility tree is briefly unavailable during Autofill transitions.
        }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)
    $null
}

function Focus-BrowserUsername {
    Adb '-s' $Serial shell am force-stop $browserPackage | Out-Null
    Adb '-s' $Serial shell am start '-W' '-a' android.intent.action.VIEW `
        '-d' $browserFixtureUrl '-p' $browserPackage | Out-Null
    Wait-Activity $browserPackage | Out-Null
    [void](Wait-Node (
        "//node[@package='$browserPackage' and " +
        "(@text='SellerKeyboard local Autofill fixture' or " +
        "@content-desc='SellerKeyboard local Autofill fixture')]"
    ) 'browser-fixture-ready')
    $displaySize = ((Adb '-s' $Serial shell wm size) -join "`n")
    $size = [regex]::Match($displaySize, '(\d+)x(\d+)')
    if (-not $size.Success) { throw "Could not parse display size: $displaySize" }
    $x = [int]([int]$size.Groups[1].Value * 0.5)
    $y = [int]([int]$size.Groups[2].Value * 0.32)
    Adb '-s' $Serial shell input tap $x $y | Out-Null
    Start-Sleep -Milliseconds 350
    [pscustomobject]@{ Width = [int]$size.Groups[1].Value; Height = [int]$size.Groups[2].Value }
}

function Open-ChromeAutofillFlag {
    Adb '-s' $Serial shell am force-stop $browserPackage | Out-Null
    Adb '-s' $Serial shell monkey '-p' $browserPackage `
        '-c' android.intent.category.LAUNCHER 1 | Out-Null
    Wait-Activity ([regex]::Escape($browserPackage)) | Out-Null
    $urlNode = Wait-Node (
        "//node[@resource-id='$browserPackage`:id/url_bar']"
    ) 'chrome-url-bar'
    $center = Get-BoundsCenter $urlNode
    Adb '-s' $Serial shell input tap $center.X $center.Y | Out-Null
    Adb '-s' $Serial shell input keycombination 113 29 | Out-Null
    Adb '-s' $Serial shell input text 'chrome://flags' | Out-Null
    Adb '-s' $Serial shell input keyevent 66 | Out-Null
    Start-Sleep -Seconds 3
    Adb '-s' $Serial shell input tap 350 280 | Out-Null
    Adb '-s' $Serial shell input keycombination 113 29 | Out-Null
    Adb '-s' $Serial shell input text `
        'enable-autofill-virtual-view-structure' | Out-Null
    Adb '-s' $Serial shell input keyevent 4 | Out-Null
    Start-Sleep -Seconds 2
    $xml = Dump-Windows 'chrome-autofill-flag'
    $titleNode = $xml.SelectSingleNode(
        "//node[@package='$browserPackage' and " +
        "@text='Enable the setting to provide a virtual view structure for Autofill']"
    )
    if (-not $titleNode) {
        throw 'Chrome did not expose its virtual-view Autofill flag.'
    }
    $xml
}

function Get-ChromeAutofillFlagState([xml]$Xml) {
    $states = @($Xml.SelectNodes(
        "//node[@package='$browserPackage' and " +
        "(@text='Default' or @text='Enabled' or @text='Disabled' or " +
        "@text='Enabled without any compatibility check' or " +
        "@text='Enabled without AwG restriction')]"
    ))
    if ($states.Count -lt 1) {
        throw 'Chrome Autofill flag state was not visible.'
    }
    [pscustomobject]@{ Value = [string]$states[0].text; Node = $states[0] }
}

function Set-ChromeAutofillFlag([string]$Target) {
    $xml = Open-ChromeAutofillFlag
    $current = Get-ChromeAutofillFlagState $xml
    if ($current.Value -eq $Target) { return $current.Value }
    $center = Get-BoundsCenter $current.Node
    Adb '-s' $Serial shell input tap $center.X $center.Y | Out-Null
    $choiceNode = Wait-Node (
        "//node[@package='$browserPackage' and " +
        "@class='android.widget.CheckedTextView' and @text='$Target']"
    ) "chrome-autofill-flag-choice-$Target"
    $center = Get-BoundsCenter $choiceNode
    Adb '-s' $Serial shell input tap $center.X $center.Y | Out-Null
    $relaunchNode = Wait-Node (
        "//node[@package='$browserPackage' and @text='Relaunch']"
    ) 'chrome-autofill-flag-relaunch'
    $center = Get-BoundsCenter $relaunchNode
    Adb '-s' $Serial shell input tap $center.X $center.Y | Out-Null
    Wait-Activity ([regex]::Escape($browserPackage)) | Out-Null
    Start-Sleep -Seconds 2
    $current.Value
}

function Open-ChromeAutofillServices {
    Adb '-s' $Serial shell am force-stop $browserPackage | Out-Null
    Adb '-s' $Serial shell monkey '-p' $browserPackage `
        '-c' android.intent.category.LAUNCHER 1 | Out-Null
    Wait-Activity ([regex]::Escape($browserPackage)) | Out-Null
    Tap-Node (
        "//node[@resource-id='$browserPackage`:id/menu_button']"
    ) 'chrome-menu'
    Tap-Node (
        "//node[@package='$browserPackage' and @text='Settings']"
    ) 'chrome-settings'
    Tap-Node (
        "//node[@package='$browserPackage' and @text='Autofill services']"
    ) 'chrome-autofill-services'
    $xml = Dump-Windows 'chrome-autofill-services'
    if (-not $xml.SelectSingleNode(
        "//node[@package='$browserPackage' and @text='Autofill with Google']"
    ) -or -not $xml.SelectSingleNode(
        "//node[@package='$browserPackage' and " +
        "@text='Autofill using another service']"
    )) {
        throw 'Chrome Autofill services choices were not visible.'
    }
    $xml
}

function Get-ChromeAutofillMode([xml]$Xml) {
    foreach ($label in @('Autofill with Google', 'Autofill using another service')) {
        $labelNode = $Xml.SelectSingleNode(
            "//node[@package='$browserPackage' and @text='$label']"
        )
        $row = $labelNode
        while ($row -and -not $row.SelectSingleNode(
            ".//node[@class='android.widget.RadioButton']"
        )) {
            $row = $row.ParentNode
        }
        $radio = if ($row) {
            $row.SelectSingleNode(".//node[@class='android.widget.RadioButton']")
        } else { $null }
        if ($radio -and [string]$radio.checked -eq 'true') { return $label }
    }
    throw 'Chrome Autofill mode had no checked radio option.'
}

function Set-ChromeAutofillMode([string]$Target) {
    $xml = Open-ChromeAutofillServices
    $current = Get-ChromeAutofillMode $xml
    if ($current -eq $Target) { return $current }
    $targetNode = $xml.SelectSingleNode(
        "//node[@package='$browserPackage' and @text='$Target']"
    )
    $center = Get-BoundsCenter $targetNode
    Adb '-s' $Serial shell input tap $center.X $center.Y | Out-Null
    $restartNode = Wait-Node (
        "//node[@package='$browserPackage' and @text='Restart Chrome']"
    ) 'chrome-autofill-restart'
    $center = Get-BoundsCenter $restartNode
    Adb '-s' $Serial shell input tap $center.X $center.Y | Out-Null
    Wait-Activity ([regex]::Escape($browserPackage)) | Out-Null
    Start-Sleep -Seconds 2
    $verified = Get-ChromeAutofillMode (Open-ChromeAutofillServices)
    if ($verified -ne $Target) {
        throw "Chrome Autofill mode did not persist: $verified"
    }
    $current
}

$originalAutofill = ((Adb '-s' $Serial shell settings get secure autofill_service) -join '').Trim()
$originalIme = ((Adb '-s' $Serial shell settings get secure default_input_method) -join '').Trim()
$originalHardKeyboard = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
$originalReverseLines = @((Adb '-s' $Serial reverse '--list') | ForEach-Object { [string]$_ })
$providerWasInstalled = Test-PackageInstalled $providerPackage
$hostWasInstalled = Test-PackageInstalled $hostPackage
$imeWasInstalled = Test-PackageInstalled $imePackage
$vaultWasPresent = Test-RemoteFile $vaultDevicePath
$browserWasInstalled = Test-PackageInstalled $browserPackage

if ($providerWasInstalled) {
    throw "Refusing to modify an existing KeePassDX installation: $providerPackage"
}
if ($vaultWasPresent) {
    throw "Refusing to overwrite an existing fixture vault: $vaultDevicePath"
}
if ($IncludeBrowserFlow -and -not $browserWasInstalled) {
    throw "Chrome is required for the named browser Autofill fixture: $browserPackage"
}
if ($IncludeBrowserFlow -and -not (Test-Path -LiteralPath $browserFixtureFile)) {
    throw "Browser fixture is missing: $browserFixtureFile"
}
if ($IncludeBrowserFlow -and @($originalReverseLines | Where-Object {
    $_ -match "tcp:$browserFixturePort\s"
}).Count) {
    throw "Refusing to replace an existing adb reverse mapping on tcp:$browserFixturePort"
}

$apkPath = Join-Path $env:TEMP "KeePassDX-$providerVersion-libre.apk"
$attributionStarted = $false
$browserServerJob = $null
$browserReverseAdded = $false
$flowResult = $null
$flowError = $null
$restorationError = $null
$imeAttribution = $null
$providerAttribution = $null
$hostAttribution = $null
$browserAttribution = $null
$originalChromeFlag = $null
$originalChromeMode = $null
$chromeFlagChanged = $false
$chromeModeChanged = $false

if ($IncludeBrowserFlow) {
    Remove-Item -LiteralPath $browserServerLog -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $browserServerReady -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $browserServerStop -Force -ErrorAction SilentlyContinue
}

try {
    $api = [int](((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '').Trim())
    if ($api -lt 30) { throw "Inline Autofill requires API 30+, found API $api" }

    if (-not $SkipBuild) {
        & $gradle :app:assembleDebug :benchmark-host:assembleBenchmark --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Fixture build failed.' }
    }

    Invoke-WebRequest -UseBasicParsing -Uri $providerUrl -OutFile $apkPath
    $downloadHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apkPath).Hash
    if ($downloadHash -ne $providerApkSha256) {
        throw "KeePassDX APK hash mismatch: $downloadHash"
    }
    $buildTools = Get-ChildItem (Join-Path $env:ANDROID_HOME 'build-tools') -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        Select-Object -First 1
    if (-not $buildTools) { throw 'Android build-tools are required for apksigner.' }
    $apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
    $signatureOutput = @(& $apksigner verify --verbose --print-certs `
        --min-sdk-version 24 $apkPath 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'KeePassDX APK signature verification failed.' }
    $signatureText = $signatureOutput -join "`n"
    $certificateMatch = [regex]::Match(
        $signatureText,
        'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})'
    )
    if (-not $certificateMatch.Success -or
        $certificateMatch.Groups[1].Value.ToLowerInvariant() -ne $providerCertificateSha256) {
        throw 'KeePassDX signing certificate did not match the project-published digest.'
    }

    $imeApk = Join-Path $repositoryRoot 'app\build\outputs\apk\debug\app-debug.apk'
    $hostApk = Join-Path $repositoryRoot (
        'benchmark-host\build\outputs\apk\benchmark\benchmark-host-benchmark.apk'
    )
    Adb '-s' $Serial install '-r' $imeApk | Out-Null
    Adb '-s' $Serial install '-r' $hostApk | Out-Null
    Adb '-s' $Serial install $apkPath | Out-Null

    $packageDump = (Adb '-s' $Serial shell dumpsys package $providerPackage) -join "`n"
    if ($packageDump -notmatch "versionCode=$providerVersionCode\b" -or
        $packageDump -notmatch "versionName=$([regex]::Escape($providerVersion))\b" -or
        $packageDump -notmatch [regex]::Escape($providerComponent.Split('/', 2)[1])) {
        throw 'Installed KeePassDX package metadata did not match the pinned fixture.'
    }

    Adb '-s' $Serial shell monkey '-p' $providerPackage `
        '-c' android.intent.category.LAUNCHER 1 | Out-Null
    Tap-Node "//node[@resource-id='$providerPackage`:id/create_database_button']" 'create-vault'
    $fileNameNode = Wait-Node (
        "//node[@package='com.google.android.documentsui' and " +
        "@class='android.widget.EditText']"
    ) 'vault-file-name'
    $fileNameCenter = Get-BoundsCenter $fileNameNode
    Adb '-s' $Serial shell input tap $fileNameCenter.X $fileNameCenter.Y | Out-Null
    Adb '-s' $Serial shell 'for i in $(seq 1 80); do input keyevent 67; done' | Out-Null
    Adb '-s' $Serial shell input text $vaultFileName | Out-Null
    Tap-Node (
        "//node[@package='com.google.android.documentsui' and " +
        "(@text='SAVE' or @text='Save')]"
    ) 'save-vault-file'

    Set-EditText 'Password' $masterPassword
    Set-EditText 'Confirm password' $masterPassword
    Tap-Node "//node[@resource-id='android:id/button1' and @text='OK']" 'master-key-ok'
    Tap-Node "//node[@resource-id='android:id/button2' and @text='Later']" `
        'notification-later'
    Wait-Activity ([regex]::Escape(
        "$providerPackage/com.kunzisoft.keepass.activities.GroupActivity"
    )) | Out-Null
    [void](Dismiss-TapTargetIfPresent 'group')

    Open-NewEntry 'native-entry'
    [void](Dismiss-TapTargetIfPresent 'entry')
    Set-EditText 'Title' $entryTitle
    Set-EditText 'Username' $username
    Set-EditText 'Password' $password

    $urlFocused = $false
    foreach ($attempt in 1..6) {
        Adb '-s' $Serial shell input keyevent 61 | Out-Null
        Start-Sleep -Milliseconds 200
        $focusXml = Dump-Windows "url-focus-$attempt"
        $focusedNode = $focusXml.SelectSingleNode(
            "//node[@class='android.widget.EditText' and @hint='URL' and @focused='true']"
        )
        if ($focusedNode) { $urlFocused = $true; break }
    }
    if (-not $urlFocused) { throw 'Could not focus KeePassDX URL field.' }
    Adb '-s' $Serial shell input text $entryUrl | Out-Null
    Start-Sleep -Milliseconds 500
    $urlNode = Wait-Node (
        "//node[@class='android.widget.EditText' and @hint='URL']"
    ) 'verify-URL'
    if ([string]$urlNode.text -ne $entryUrl) {
        throw "Could not set URL exactly. Actual: $($urlNode.text)"
    }
    Complete-EntrySave 'native-entry'

    if ($IncludeBrowserFlow) {
        Open-NewEntry 'browser-entry'
        [void](Dismiss-TapTargetIfPresent 'browser-entry')
        Set-EditText 'Title' $browserEntryTitle
        Set-EditText 'Username' $username
        Set-EditText 'Password' $password
        $browserUrlFocused = $false
        foreach ($attempt in 1..6) {
            Adb '-s' $Serial shell input keyevent 61 | Out-Null
            Start-Sleep -Milliseconds 200
            $focusXml = Dump-Windows "browser-url-focus-$attempt"
            $focusedNode = $focusXml.SelectSingleNode(
                "//node[@class='android.widget.EditText' and @hint='URL' and @focused='true']"
            )
            if ($focusedNode) { $browserUrlFocused = $true; break }
        }
        if (-not $browserUrlFocused) {
            throw 'Could not focus KeePassDX browser URL field.'
        }
        Adb '-s' $Serial shell input text $browserEntryUrl | Out-Null
        Start-Sleep -Milliseconds 500
        $browserUrlNode = Wait-Node (
            "//node[@class='android.widget.EditText' and @hint='URL']"
        ) 'verify-browser-URL'
        if ([string]$browserUrlNode.text -ne $browserEntryUrl) {
            throw "Could not set browser URL exactly. Actual: $($browserUrlNode.text)"
        }
        Complete-EntrySave 'browser-entry'
    }

    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Adb '-s' $Serial shell settings put secure autofill_service $providerComponent | Out-Null
    Adb '-s' $Serial shell ime enable $imeComponent | Out-Null
    Adb '-s' $Serial shell ime set $imeComponent | Out-Null

    if ($IncludeBrowserFlow) {
    $chromePackageDump = (Adb '-s' $Serial shell dumpsys package $browserPackage) -join "`n"
    $chromeVersionMatch = [regex]::Match($chromePackageDump, 'versionName=([^\s]+)')
    if (-not $chromeVersionMatch.Success) { throw 'Could not read Chrome version.' }
    $chromeVersion = $chromeVersionMatch.Groups[1].Value

    $browserServerJob = Start-Job -ScriptBlock {
        param($Port, $FixturePath, $FixtureFile, $LogPath, $ReadyPath, $StopPath)
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
        $listener.Start()
        Set-Content -LiteralPath $ReadyPath -Encoding utf8 -Value 'ready'
        try {
            while (-not (Test-Path -LiteralPath $StopPath)) {
                if (-not $listener.Pending()) {
                    Start-Sleep -Milliseconds 50
                    continue
                }
                $client = $listener.AcceptTcpClient()
                try {
                    $stream = $client.GetStream()
                    $reader = [IO.StreamReader]::new(
                        $stream,
                        [Text.Encoding]::ASCII,
                        $false,
                        1024,
                        $true
                    )
                    $requestLine = $reader.ReadLine()
                    do { $line = $reader.ReadLine() } while (
                        $null -ne $line -and $line.Length -gt 0
                    )
                    Add-Content -LiteralPath $LogPath -Encoding utf8 -Value $requestLine
                    $request = [regex]::Match(
                        [string]$requestLine,
                        '^(GET|HEAD)\s+([^\s]+)\s+HTTP/'
                    )
                    $method = if ($request.Success) { $request.Groups[1].Value } else { '' }
                    $path = if ($request.Success) { $request.Groups[2].Value } else { '' }
                    if ($path -eq $FixturePath) {
                        $body = [IO.File]::ReadAllBytes($FixtureFile)
                        $status = '200 OK'
                        $contentType = 'text/html; charset=utf-8'
                    } elseif ($path -eq '/favicon.ico') {
                        $body = [byte[]]::new(0)
                        $status = '204 No Content'
                        $contentType = 'image/x-icon'
                    } else {
                        $body = [Text.Encoding]::UTF8.GetBytes('not found')
                        $status = '404 Not Found'
                        $contentType = 'text/plain; charset=utf-8'
                    }
                    $header = "HTTP/1.1 $status`r`nContent-Type: $contentType`r`n" +
                        "Content-Length: $($body.Length)`r`nCache-Control: no-store`r`n" +
                        "Connection: close`r`n`r`n"
                    $headerBytes = [Text.Encoding]::ASCII.GetBytes($header)
                    $stream.Write($headerBytes, 0, $headerBytes.Length)
                    if ($method -ne 'HEAD' -and $body.Length -gt 0) {
                        $stream.Write($body, 0, $body.Length)
                    }
                    $stream.Flush()
                } catch [IO.IOException] {
                    continue
                } finally {
                    $client.Dispose()
                }
            }
        } finally {
            $listener.Stop()
        }
    } -ArgumentList $browserFixturePort, $browserFixturePath, $browserFixtureFile, `
        $browserServerLog, $browserServerReady, $browserServerStop
    $serverDeadline = (Get-Date).AddSeconds(15)
    do { Start-Sleep -Milliseconds 100 } while (
        -not (Test-Path -LiteralPath $browserServerReady) -and
        (Get-Date) -lt $serverDeadline
    )
    if (-not (Test-Path -LiteralPath $browserServerReady)) {
        throw 'Local Chrome Autofill fixture server did not start.'
    }
    Adb '-s' $Serial reverse "tcp:$browserFixturePort" "tcp:$browserFixturePort" | Out-Null
    $browserReverseAdded = $true
    }

    $imeAttributionRoot = Join-Path $OutputDirectory 'network-attribution\ime'
    $providerAttributionRoot = Join-Path $OutputDirectory 'network-attribution\provider'
    $hostAttributionRoot = Join-Path $OutputDirectory 'network-attribution\host'
    $browserAttributionRoot = Join-Path $OutputDirectory 'network-attribution\browser'
    Start-Attribution $imePackage $imeAttributionRoot
    Start-Attribution $providerPackage $providerAttributionRoot
    Start-Attribution $hostPackage $hostAttributionRoot
    if ($IncludeBrowserFlow) {
        Start-Attribution $browserPackage $browserAttributionRoot Record
    }
    $attributionStarted = $true

    Adb '-s' $Serial shell am force-stop $hostPackage | Out-Null
    Adb '-s' $Serial shell am start '-W' '-n' $hostComponent | Out-Null
    $imeInitial = Wait-BridgeValue $imeBridge snapshot inline_count @('1') 30
    $imeInitial = Wait-BridgeValue $imeBridge snapshot candidate_source `
        @('INLINE_AUTOFILL') 20
    if ((Bundle-Value $imeInitial 'editor_package') -ne $hostPackage) {
        throw "SellerKeyboard did not expose the initial KeePassDX inline dataset: $imeInitial"
    }
    $initialCapture = Capture-Window 'initial-sign-in'
    $signInTap = Get-InlineTap $initialCapture.Xml 0.25
    Adb '-s' $Serial shell input tap $signInTap.X $signInTap.Y | Out-Null

    Wait-Activity ([regex]::Escape(
        "$providerPackage/com.kunzisoft.keepass.activities.GroupActivity"
    )) | Out-Null
    [void](Dismiss-TapTargetIfPresent 'autofill-selection')
    Tap-Node (
        "//node[@resource-id='$providerPackage`:id/node_container' and " +
        ".//node[@text='$entryTitle']]"
    ) 'select-entry'
    Wait-Activity ([regex]::Escape($hostComponent)) | Out-Null
    if ($IncludeBrowserFlow) {
        Adb '-s' $Serial shell am force-stop $hostPackage | Out-Null
        Adb '-s' $Serial shell am start '-W' '-n' $hostComponent | Out-Null
        [void](Wait-BridgeValue $hostBridge autofill_snapshot `
            autofill_username_focused @('true') 20)
        $imeSelected = Wait-BridgeValue $imeBridge snapshot inline_count @('1', '2') 30
    } else {
        $imeSelected = Wait-BridgeValue $imeBridge snapshot inline_count @('2') 30
    }
    $expectedUsernameHash = Sha256-Text $username
    $expectedPasswordHash = Sha256-Text $password
    $selectedCapture = Capture-Window 'selected-entry-datasets'
    $selectedCaptureInitial = $selectedCapture
    $credentialTap = $null
    $hostAfter = $null
    foreach ($fraction in @(0.25, 0.75)) {
        $candidateTap = Get-InlineTap $selectedCapture.Xml $fraction
        Adb '-s' $Serial shell input tap $candidateTap.X $candidateTap.Y | Out-Null
        $hostAfter = Try-Wait-BridgeValue $hostBridge autofill_snapshot `
            autofill_username_sha256 @($expectedUsernameHash) 5
        if ($hostAfter) {
            $credentialTap = [ordered]@{
                x = $candidateTap.X
                y = $candidateTap.Y
                numberRowTop = $candidateTap.NumberRowTop
                horizontalFraction = $fraction
            }
            break
        }

        $activities = (Adb '-s' $Serial shell dumpsys activity activities) -join "`n"
        if ($activities -match [regex]::Escape(
            "$providerPackage/com.kunzisoft.keepass.activities.GroupActivity"
        )) {
            Adb '-s' $Serial shell input keyevent 4 | Out-Null
            Wait-Activity ([regex]::Escape($hostComponent)) | Out-Null
            $imeSelected = Wait-BridgeValue $imeBridge snapshot inline_count @('1', '2') 20
        }
        $selectedCapture = Capture-Window "selected-entry-datasets-retry-$fraction"
    }
    if (-not $hostAfter -or -not $credentialTap) {
        throw 'Neither KeePassDX inline chip produced the exact controlled fill.'
    }
    if ([int](Bundle-Value $hostAfter 'autofill_username_length') -ne $username.Length -or
        (Bundle-Value $hostAfter 'autofill_password_sha256') -ne $expectedPasswordHash -or
        [int](Bundle-Value $hostAfter 'autofill_password_length') -ne $password.Length) {
        throw "KeePassDX did not fill both exact controlled fields: $hostAfter"
    }
    $imeAfter = Wait-BridgeValue $imeBridge snapshot inline_count @('0') 20
    $afterCapture = Capture-Window 'after-selection'

    if ($IncludeBrowserFlow) {
    $originalChromeFlag = (Get-ChromeAutofillFlagState (
        Open-ChromeAutofillFlag
    )).Value
    if ($originalChromeFlag -ne 'Default') {
        throw "Refusing to replace an existing Chrome Autofill flag state: $originalChromeFlag"
    }
    $chromeFlagChanged = $true
    [void](Set-ChromeAutofillFlag 'Enabled')
    $originalChromeMode = Get-ChromeAutofillMode (Open-ChromeAutofillServices)
    if ($originalChromeMode -ne 'Autofill with Google') {
        throw "Refusing to replace an existing Chrome Autofill mode: $originalChromeMode"
    }
    $chromeModeChanged = $true
    [void](Set-ChromeAutofillMode 'Autofill using another service')

    $browserDisplay = Focus-BrowserUsername
    $browserImeInitial = Wait-BridgeValue $imeBridge snapshot editor_package `
        @($browserPackage) 30
    $browserPresentationScreenshots = @(
        Capture-Screenshot 'browser-anchored-presentation-1'
    )
    $expectedBrowserStatus = (
        "username-length=$($username.Length) username-sha256=$expectedUsernameHash " +
        "password-length=$($password.Length) password-sha256=$expectedPasswordHash"
    )
    $browserCredentialTap = $null
    $browserProviderPickerOpened = $false
    $browserFilled = $false
    $presentationYFractions = @(0.37, 0.39, 0.35, 0.41)
    :browserAttempts foreach ($attempt in 1..$presentationYFractions.Count) {
        if ($attempt -gt 1) {
            $usernameX = [int]($browserDisplay.Width * 0.5)
            $usernameY = [int]($browserDisplay.Height * 0.32)
            Adb '-s' $Serial shell input tap $usernameX $usernameY | Out-Null
            Start-Sleep -Milliseconds 350
            $browserPresentationScreenshots += Capture-Screenshot `
                "browser-anchored-presentation-$attempt"
        }
        $candidateX = [int]($browserDisplay.Width * 0.5)
        $candidateYFraction = $presentationYFractions[$attempt - 1]
        $candidateY = [int]($browserDisplay.Height * $candidateYFraction)
        Adb '-s' $Serial shell input tap $candidateX $candidateY | Out-Null
        Start-Sleep -Milliseconds 600
        $activities = (Adb '-s' $Serial shell dumpsys activity activities) -join "`n"
        $providerGroupActivity = [regex]::Escape(
            "$providerPackage/com.kunzisoft.keepass.activities.GroupActivity"
        )
        if ($activities -notmatch "topResumedActivity=.*$providerGroupActivity") {
            Adb '-s' $Serial shell input keyevent 4 | Out-Null
            Start-Sleep -Milliseconds 500
        }
        $browserStatusXml = Wait-BrowserStatus $expectedBrowserStatus 8
        if ($browserStatusXml) {
            $browserCredentialTap = [ordered]@{
                x = $candidateX
                y = $candidateY
                presentation = 'PLATFORM_ANCHORED_AUTOFILL'
                verticalFraction = $candidateYFraction
                attempt = $attempt
            }
            $browserFilled = $true
            break browserAttempts
        }

        $activities = (Adb '-s' $Serial shell dumpsys activity activities) -join "`n"
        if ($activities -match "topResumedActivity=.*$providerGroupActivity") {
            $browserProviderPickerOpened = $true
            [void](Dismiss-TapTargetIfPresent "browser-autofill-selection-$attempt")
            Tap-Node (
                "//node[@resource-id='$providerPackage`:id/node_container' and " +
                ".//node[@text='$browserEntryTitle']]"
            ) "browser-select-entry-$attempt"
            Wait-Activity ([regex]::Escape($browserPackage)) | Out-Null
        }
    }
    if (-not $browserFilled -or -not $browserCredentialTap) {
        throw 'KeePassDX did not produce the exact controlled fill in local Chrome.'
    }
    $browserImeAfter = Wait-BridgeValue $imeBridge snapshot editor_package `
        @($browserPackage) 20
    $browserAfterCapture = Capture-Window 'browser-after-selection'
    $browserAfterXml = Get-Content -Raw -Encoding UTF8 `
        -LiteralPath $browserAfterCapture.Xml
    if ($browserAfterXml -notmatch [regex]::Escape($expectedBrowserStatus)) {
        throw 'Chrome accessibility output did not retain the exact hash-only fill result.'
    }
    Start-Sleep -Milliseconds 500
    $browserRequests = if (Test-Path -LiteralPath $browserServerLog) {
        @(Get-Content -LiteralPath $browserServerLog)
    } else { @() }
    $fixtureRequests = @($browserRequests | Where-Object {
        $_ -match "^(GET|HEAD)\s+$([regex]::Escape($browserFixturePath))\s+HTTP/"
    })
    $unsafeRequests = @($browserRequests | Where-Object {
        $_ -notmatch '^(GET|HEAD)\s+[^\s]+\s+HTTP/' -or
        $_ -match [regex]::Escape($username) -or
        $_ -match [regex]::Escape($password)
    })
    if ($fixtureRequests.Count -lt 1 -or $unsafeRequests.Count -ne 0) {
        throw "Local browser request boundary failed: $($browserRequests -join ' | ')"
    }
    }

    $imeAttribution = Finish-Attribution $imePackage $imeAttributionRoot
    $providerAttribution = Finish-Attribution $providerPackage $providerAttributionRoot
    $hostAttribution = Finish-Attribution $hostPackage $hostAttributionRoot
    if ($IncludeBrowserFlow) {
        $browserAttribution = Finish-Attribution $browserPackage $browserAttributionRoot Record
    }
    $attributionStarted = $false
    foreach ($attribution in @($imeAttribution, $providerAttribution, $hostAttribution)) {
        if (-not [bool]$attribution.networkAccountingSupported -or
            [long]$attribution.rxDelta -ne 0 -or [long]$attribution.txDelta -ne 0 -or
            [int]$attribution.canaryOccurrencesInUidLog -ne 0) {
            throw 'Named-provider UID privacy attribution did not remain zero.'
        }
    }
    if ($IncludeBrowserFlow -and (
        -not [bool]$browserAttribution.networkAccountingSupported -or
        [int]$browserAttribution.canaryOccurrencesInUidLog -ne 0
    )) {
        throw 'Chrome UID attribution was unsupported or leaked a controlled canary.'
    }
    $observedUids = @(
        [int]$imeAttribution.uid,
        [int]$providerAttribution.uid,
        [int]$hostAttribution.uid
    )
    if ($IncludeBrowserFlow) { $observedUids += [int]$browserAttribution.uid }
    $expectedUidCount = if ($IncludeBrowserFlow) { 4 } else { 3 }
    if (@($observedUids | Sort-Object -Unique).Count -ne $expectedUidCount) {
        throw "Observed packages were not isolated under $expectedUidCount UIDs: $observedUids"
    }

    $artifactPaths = @(
        $initialCapture.Xml,
        $initialCapture.Screenshot,
        $selectedCaptureInitial.Xml,
        $selectedCaptureInitial.Screenshot,
        $selectedCapture.Xml,
        $selectedCapture.Screenshot,
        $afterCapture.Xml,
        $afterCapture.Screenshot
    )
    if ($IncludeBrowserFlow) {
        $artifactPaths += @(
            $browserPresentationScreenshots,
            $browserAfterCapture.Xml,
            $browserAfterCapture.Screenshot,
            $browserServerLog
        )
    }
    $artifactPaths = @($artifactPaths | Sort-Object -Unique)
    $artifactManifest = @($artifactPaths | ForEach-Object { File-Metadata $_ })

    $flowResult = [ordered]@{
        api = $api
        provider = [ordered]@{
            name = 'KeePassDX'
            package = $providerPackage
            component = $providerComponent
            version = $providerVersion
            versionCode = $providerVersionCode
            officialReleaseUrl = $providerUrl
            apkSha256 = $downloadHash
            signingCertificateSha256 = $providerCertificateSha256
            localVault = $true
            cloudAccountUsed = $false
        }
        packages = [ordered]@{
            ime = $imePackage
            imeUid = [int]$imeAttribution.uid
            provider = $providerPackage
            providerUid = [int]$providerAttribution.uid
            host = $hostPackage
            hostUid = [int]$hostAttribution.uid
            browser = $browserPackage
            browserUid = [int]$browserAttribution.uid
            distinctUids = $true
        }
        initial = [ordered]@{
            candidateSource = Bundle-Value $imeInitial 'candidate_source'
            inlineCount = [int](Bundle-Value $imeInitial 'inline_count')
            signInTap = $signInTap
        }
        entrySelection = [ordered]@{
            providerOwnedPickerOpened = $true
            inlineCountAfterSelection = [int](Bundle-Value $imeSelected 'inline_count')
            credentialTap = $credentialTap
        }
        fill = [ordered]@{
            usernameLength = $username.Length
            usernameSha256 = $expectedUsernameHash
            passwordLength = $password.Length
            passwordSha256 = $expectedPasswordHash
            inlineCountAfterFill = [int](Bundle-Value $imeAfter 'inline_count')
        }
        browser = [ordered]@{
            package = $browserPackage
            version = $chromeVersion
            fixtureUrl = $browserFixtureUrl
            fixtureTransport = 'adb-reversed host loopback HTTP'
            localOnly = $true
            virtualViewFlag = 'Enabled'
            virtualViewFlagSource = (
                'chrome://flags/#enable-autofill-virtual-view-structure'
            )
            autofillMode = 'Autofill using another service'
            originalVirtualViewFlag = $originalChromeFlag
            originalAutofillMode = $originalChromeMode
            platformPresentation = 'PLATFORM_ANCHORED_AUTOFILL'
            inlinePresentationExpected = $false
            providerOwnedPickerOpened = $browserProviderPickerOpened
            candidateSource = Bundle-Value $browserImeInitial 'candidate_source'
            initialInlineCount = [int](Bundle-Value $browserImeInitial 'inline_count')
            credentialTap = $browserCredentialTap
            usernameLength = $username.Length
            usernameSha256 = $expectedUsernameHash
            passwordLength = $password.Length
            passwordSha256 = $expectedPasswordHash
            hashOnlyDomAssertion = $expectedBrowserStatus
            inlineCountAfterFill = [int](Bundle-Value $browserImeAfter 'inline_count')
            requestCount = $browserRequests.Count
            fixtureRequestCount = $fixtureRequests.Count
            unsafeRequestCount = $unsafeRequests.Count
            noSubmission = $true
        }
        privacy = [ordered]@{
            ime = $imeAttribution
            provider = $providerAttribution
            host = $hostAttribution
            browser = $browserAttribution
        }
        artifacts = $artifactManifest
    }
} catch {
    $flowError = $_
} finally {
    if ($attributionStarted) {
        try { $imeAttribution = Finish-Attribution $imePackage $imeAttributionRoot } catch {}
        try { $providerAttribution = Finish-Attribution $providerPackage $providerAttributionRoot } catch {}
        try { $hostAttribution = Finish-Attribution $hostPackage $hostAttributionRoot } catch {}
        if ($IncludeBrowserFlow) { try {
            $browserAttribution = Finish-Attribution $browserPackage `
                $browserAttributionRoot Record
        } catch {} }
    }
    try {
        if ($IncludeBrowserFlow) {
        Adb '-s' $Serial shell am force-stop $browserPackage | Out-Null
        if ($browserReverseAdded) {
            Adb '-s' $Serial reverse '--remove' "tcp:$browserFixturePort" | Out-Null
            $browserReverseAdded = $false
        }
        if ($browserServerJob) {
            Set-Content -LiteralPath $browserServerStop -Encoding utf8 -Value 'stop'
            $null = Wait-Job -Job $browserServerJob -Timeout 5
            if ($browserServerJob.State -notin @('Completed', 'Failed', 'Stopped')) {
                Stop-Job -Job $browserServerJob -ErrorAction SilentlyContinue
            }
            Remove-Job -Job $browserServerJob -Force -ErrorAction SilentlyContinue
            $browserServerJob = $null
        }
        if ($chromeModeChanged) {
            [void](Set-ChromeAutofillMode $originalChromeMode)
            $chromeModeChanged = $false
        }
        if ($chromeFlagChanged) {
            [void](Set-ChromeAutofillFlag $originalChromeFlag)
            $chromeFlagChanged = $false
        }
        }
        Restore-Setting secure autofill_service $originalAutofill
        Restore-Setting secure default_input_method $originalIme
        Restore-Setting secure show_ime_with_hard_keyboard $originalHardKeyboard
        Adb '-s' $Serial shell am force-stop $providerPackage | Out-Null
        if (Test-PackageInstalled $providerPackage) {
            Adb '-s' $Serial uninstall $providerPackage | Out-Null
        }
        if (Test-RemoteFile $vaultDevicePath) {
            Adb '-s' $Serial shell rm $vaultDevicePath | Out-Null
        }
        if (-not $hostWasInstalled -and (Test-PackageInstalled $hostPackage)) {
            Adb '-s' $Serial uninstall $hostPackage | Out-Null
        }
        if (-not $imeWasInstalled -and (Test-PackageInstalled $imePackage)) {
            Adb '-s' $Serial uninstall $imePackage | Out-Null
        }
        if (Test-Path -LiteralPath $apkPath) {
            Remove-Item -LiteralPath $apkPath -Force
        }
        Remove-Item -LiteralPath $browserServerReady -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $browserServerStop -Force -ErrorAction SilentlyContinue
    } catch {
        $restorationError = $_
    }
}

$restoredAutofill = ((Adb '-s' $Serial shell settings get secure autofill_service) -join '').Trim()
$restoredIme = ((Adb '-s' $Serial shell settings get secure default_input_method) -join '').Trim()
$restoredHardKeyboard = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
$restoredReverseLines = @((Adb '-s' $Serial reverse '--list') | ForEach-Object { [string]$_ })
$restoration = [ordered]@{
    autofillServiceExact = $restoredAutofill -eq $originalAutofill
    defaultImeExact = $restoredIme -eq $originalIme
    showImeWithHardKeyboardExact = $restoredHardKeyboard -eq $originalHardKeyboard
    providerPresenceExact = (Test-PackageInstalled $providerPackage) -eq $providerWasInstalled
    hostPresenceExact = (Test-PackageInstalled $hostPackage) -eq $hostWasInstalled
    imePresenceExact = (Test-PackageInstalled $imePackage) -eq $imeWasInstalled
    fixtureVaultPresenceExact = (Test-RemoteFile $vaultDevicePath) -eq $vaultWasPresent
    browserPresenceExact = (Test-PackageInstalled $browserPackage) -eq $browserWasInstalled
    browserReverseExact = (@($restoredReverseLines) -join "`n") -eq `
        (@($originalReverseLines) -join "`n")
    browserServerStopped = $null -eq $browserServerJob
    browserAutofillModeExact = -not $chromeModeChanged
    browserAutofillFlagExact = -not $chromeFlagChanged
}
$restorationPassed = @($restoration.Values | Where-Object { -not $_ }).Count -eq 0

$summary = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString('o')
    overall = if ($flowError -or $restorationError -or -not $restorationPassed) {
        'FAIL'
    } else {
        'PASS'
    }
    serial = $Serial
    flow = $flowResult
    restoration = $restoration
    error = if ($flowError) { $flowError.Exception.Message } elseif ($restorationError) {
        $restorationError.Exception.Message
    } elseif (-not $restorationPassed) {
        'Device state restoration was incomplete.'
    } else {
        $null
    }
}
$summaryPath = Join-Path $OutputDirectory 'summary.json'
$summary | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 $summaryPath

if ($summary.overall -ne 'PASS') {
    throw "Named password-manager Autofill verification failed: $($summary.error). See $summaryPath"
}
Write-Host "Named KeePassDX Autofill verification passed: $summaryPath"
