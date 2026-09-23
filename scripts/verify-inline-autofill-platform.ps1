[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
} else {
    'adb'
}
$gradle = Join-Path $repositoryRoot 'gradlew.bat'
$imePackage = 'com.terasumi.sellerkeyboard'
$hostPackage = 'com.terasumi.sellerkeyboard.benchmarkhost'
$providerPackage = 'com.terasumi.sellerkeyboard.autofilltest'
$imeComponent = "$imePackage/.service.SellerKeyboard"
$providerComponent = "$providerPackage/.DeterministicAutofillService"
$hostActivity = "$hostPackage/.AutofillBenchmarkActivity"
$imeBridge = "content://$imePackage.ime-test-bridge"
$hostBridge = "content://$hostPackage.host-test-bridge"
$providerBridge = "content://$providerPackage.test-bridge"
$username = 'seller-autofill@example.invalid'
$password = 'Deterministic-Autofill-42!'
$datasetLabel = 'Seller test credential'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Get-Sha256Hex([string]$Path) {
    $stream = [System.IO.File]::OpenRead($Path)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [System.BitConverter]::ToString($sha256.ComputeHash($stream)).Replace('-', '')
    } finally {
        $sha256.Dispose()
        $stream.Dispose()
    }
}

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

function Sha256-Text([string]$Value) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
    -join ($hash | ForEach-Object { $_.ToString('x2') })
}

function Test-PackageInstalled([string]$PackageName) {
    $output = @(& $adb '-s' $Serial shell pm path $PackageName 2>$null)
    $exitCode = $LASTEXITCODE
    $exitCode -eq 0 -and (($output -join "`n") -match '^package:')
}

function Capture-Window([string]$Name) {
    $remoteXml = "/sdcard/inline-autofill-$Name.xml"
    $remotePng = "/sdcard/inline-autofill-$Name.png"
    $localXml = Join-Path $OutputDirectory "$Name-window.xml"
    $localPng = Join-Path $OutputDirectory "$Name.png"
    Adb '-s' $Serial shell uiautomator dump '--windows' $remoteXml | Out-Null
    Adb '-s' $Serial pull '-q' $remoteXml $localXml | Out-Null
    Adb '-s' $Serial shell screencap '-p' $remotePng | Out-Null
    Adb '-s' $Serial pull '-q' $remotePng $localPng | Out-Null
    [pscustomobject]@{ Xml = $localXml; Screenshot = $localPng }
}

function Wait-BridgeValue(
    [string]$Uri,
    [string]$Method,
    [string]$Key,
    [string[]]$Expected,
    [int]$Seconds = 30
) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $snapshot = Call-Bridge $Uri $Method
        if ((Bundle-Value $snapshot $Key) -in $Expected) { return $snapshot }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Key in [$($Expected -join ', ')]. Last: $snapshot"
}

function Start-Attribution([string]$PackageName, [string]$Directory) {
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Start -Serial $Serial -PackageName $PackageName `
        -OutputDirectory $Directory -NetworkExpectation Zero
}

function Finish-Attribution([string]$PackageName, [string]$Directory) {
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Finish -Serial $Serial -PackageName $PackageName `
        -OutputDirectory $Directory -NetworkExpectation Zero `
        -Canary @($username, $password, $datasetLabel)
    Get-Content -Raw -LiteralPath (Join-Path $Directory 'summary.json') | ConvertFrom-Json
}

function Restore-Setting([string]$Namespace, [string]$Key, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -eq 'null') {
        Adb '-s' $Serial shell settings delete $Namespace $Key | Out-Null
    } else {
        Adb '-s' $Serial shell settings put $Namespace $Key $Value | Out-Null
    }
}

$originalAutofill = ((Adb '-s' $Serial shell settings get secure autofill_service) -join '').Trim()
$originalIme = ((Adb '-s' $Serial shell settings get secure default_input_method) -join '').Trim()
$originalHardKeyboard = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
$providerWasInstalled = Test-PackageInstalled $providerPackage
$hostWasInstalled = Test-PackageInstalled $hostPackage
$imeWasInstalled = Test-PackageInstalled $imePackage
$flowResult = $null
$flowError = $null
$restorationError = $null

try {
    & $gradle :app:assembleDebug :benchmark-host:assembleBenchmark `
        :autofill-test-provider:assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Inline-autofill fixture build failed.' }

    $imeApk = Join-Path $repositoryRoot 'app\build\outputs\apk\debug\app-debug.apk'
    $hostApk = Join-Path $repositoryRoot `
        'benchmark-host\build\outputs\apk\benchmark\benchmark-host-benchmark.apk'
    $providerApk = Join-Path $repositoryRoot `
        'autofill-test-provider\build\outputs\apk\debug\autofill-test-provider-debug.apk'
    Adb '-s' $Serial install '-r' $imeApk | Out-Null
    Adb '-s' $Serial install '-r' $hostApk | Out-Null
    Adb '-s' $Serial install '-r' $providerApk | Out-Null

    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Adb '-s' $Serial shell ime enable $imeComponent | Out-Null
    Adb '-s' $Serial shell ime set $imeComponent | Out-Null
    Adb '-s' $Serial shell settings put secure autofill_service $providerComponent | Out-Null
    Adb '-s' $Serial shell cmd autofill reset | Out-Null
    Call-Bridge $providerBridge 'reset' | Out-Null

    $imeAttributionRoot = Join-Path $OutputDirectory 'network-attribution\ime'
    $providerAttributionRoot = Join-Path $OutputDirectory 'network-attribution\provider'
    $hostAttributionRoot = Join-Path $OutputDirectory 'network-attribution\host'
    Start-Attribution $imePackage $imeAttributionRoot
    Start-Attribution $providerPackage $providerAttributionRoot
    Start-Attribution $hostPackage $hostAttributionRoot

    $providerSnapshot = $null
    $imeSnapshot = $null
    foreach ($attempt in 1..2) {
        try {
            Adb '-s' $Serial shell am force-stop $hostPackage | Out-Null
            Adb '-s' $Serial shell am start '-W' '-n' $hostActivity | Out-Null
            $providerSnapshot = Wait-BridgeValue $providerBridge 'snapshot' `
                'dataset_returned' @('true') 30
            $imeSnapshot = Wait-BridgeValue $imeBridge 'snapshot' 'inline_count' @('1') 30
            break
        } catch {
            if ($attempt -eq 2) { throw }
            Adb '-s' $Serial shell am force-stop $hostPackage | Out-Null
            Adb '-s' $Serial shell cmd autofill reset | Out-Null
            Call-Bridge $providerBridge 'reset' | Out-Null
            Adb '-s' $Serial shell ime set $imeComponent | Out-Null
            Start-Sleep -Seconds 2
        }
    }
    $hostBefore = Call-Bridge $hostBridge 'autofill_snapshot'

    if ((Bundle-Value $providerSnapshot 'inline_request_present') -ne 'true' -or
        [int](Bundle-Value $providerSnapshot 'max_suggestion_count') -lt 1 -or
        (Bundle-Value $providerSnapshot 'target_package') -ne $hostPackage -or
        [int](Bundle-Value $providerSnapshot 'request_count') -lt 1) {
        throw "Provider did not receive and answer the real inline request: $providerSnapshot"
    }
    if ((Bundle-Value $imeSnapshot 'candidate_source') -ne 'INLINE_AUTOFILL' -or
        (Bundle-Value $imeSnapshot 'editor_package') -ne $hostPackage -or
        (Bundle-Value $imeSnapshot 'sensitive') -ne 'true') {
        throw "SellerKeyboard did not expose the system-owned inline dataset: $imeSnapshot"
    }
    if ((Bundle-Value $hostBefore 'active') -ne 'true' -or
        [int](Bundle-Value $hostBefore 'autofill_username_length') -ne 0 -or
        [int](Bundle-Value $hostBefore 'autofill_password_length') -ne 0) {
        throw "Host fields were not empty before selection: $hostBefore"
    }

    $beforeCapture = Capture-Window 'before-selection'
    $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $beforeCapture.Xml
    $numberRowNodes = @([regex]::Matches(
        $xml,
        '<node[^>]*(?:text|content-desc)="1"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*/?>'
    ) | Where-Object {
        [int]$_.Groups[3].Value - [int]$_.Groups[1].Value -ge 60
    })
    if ($numberRowNodes.Count -eq 0) {
        throw "Could not locate the SellerKeyboard number row in $($beforeCapture.Xml)"
    }
    $numberRowTop = @($numberRowNodes | ForEach-Object {
        [int]$_.Groups[2].Value
    } | Sort-Object | Select-Object -First 1)[0]
    $displaySize = ((Adb '-s' $Serial shell wm size) -join "`n")
    $widthMatch = [regex]::Match($displaySize, '(\d+)x(\d+)')
    if (-not $widthMatch.Success) { throw "Could not parse display size: $displaySize" }
    $tapX = [int]([int]$widthMatch.Groups[1].Value * 0.25)
    $tapY = $numberRowTop - 80
    Adb '-s' $Serial shell input tap $tapX $tapY | Out-Null

    $expectedUsernameHash = Sha256-Text $username
    $expectedPasswordHash = Sha256-Text $password
    $hostAfter = Wait-BridgeValue $hostBridge 'autofill_snapshot' `
        'autofill_username_sha256' @($expectedUsernameHash) 20
    if ([int](Bundle-Value $hostAfter 'autofill_username_length') -ne $username.Length -or
        (Bundle-Value $hostAfter 'autofill_password_sha256') -ne $expectedPasswordHash -or
        [int](Bundle-Value $hostAfter 'autofill_password_length') -ne $password.Length) {
        throw "Platform selection did not fill both exact controlled fields: $hostAfter"
    }
    $imeAfter = Wait-BridgeValue $imeBridge 'snapshot' 'inline_count' @('0') 20
    $afterCapture = Capture-Window 'after-selection'

    $imeAttribution = Finish-Attribution $imePackage $imeAttributionRoot
    $providerAttribution = Finish-Attribution $providerPackage $providerAttributionRoot
    $hostAttribution = Finish-Attribution $hostPackage $hostAttributionRoot
    foreach ($attribution in @($imeAttribution, $providerAttribution, $hostAttribution)) {
        if (-not $attribution.networkAccountingSupported -or
            [long]$attribution.rxDelta -ne 0 -or [long]$attribution.txDelta -ne 0 -or
            [int]$attribution.canaryOccurrencesInUidLog -ne 0) {
            throw "Inline-autofill UID attribution violated the zero boundary: " +
                ($attribution | ConvertTo-Json -Compress)
        }
    }

    $flowResult = [ordered]@{
        schemaVersion = 1
        capturedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        api = [int](((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '').Trim())
        imePackage = $imePackage
        hostPackage = $hostPackage
        providerPackage = $providerPackage
        providerRequestCount = [int](Bundle-Value $providerSnapshot 'request_count')
        inlineRequestPresent = (Bundle-Value $providerSnapshot 'inline_request_present') -eq 'true'
        maxSuggestionCount = [int](Bundle-Value $providerSnapshot 'max_suggestion_count')
        datasetReturned = (Bundle-Value $providerSnapshot 'dataset_returned') -eq 'true'
        imeInlineCountBefore = [int](Bundle-Value $imeSnapshot 'inline_count')
        imeCandidateSourceBefore = Bundle-Value $imeSnapshot 'candidate_source'
        imeSensitiveBefore = (Bundle-Value $imeSnapshot 'sensitive') -eq 'true'
        imeInlineCountAfter = [int](Bundle-Value $imeAfter 'inline_count')
        usernameLength = [int](Bundle-Value $hostAfter 'autofill_username_length')
        usernameSha256 = Bundle-Value $hostAfter 'autofill_username_sha256'
        passwordLength = [int](Bundle-Value $hostAfter 'autofill_password_length')
        passwordSha256 = Bundle-Value $hostAfter 'autofill_password_sha256'
        tap = [ordered]@{ x = $tapX; y = $tapY; numberRowTop = $numberRowTop }
        privacy = [ordered]@{
            ime = $imeAttribution
            provider = $providerAttribution
            host = $hostAttribution
        }
        artifacts = @(
            [ordered]@{
                file = 'before-selection.png'
                bytes = (Get-Item -LiteralPath $beforeCapture.Screenshot).Length
                sha256 = Get-Sha256Hex -Path $beforeCapture.Screenshot
            }
            [ordered]@{
                file = 'after-selection.png'
                bytes = (Get-Item -LiteralPath $afterCapture.Screenshot).Length
                sha256 = Get-Sha256Hex -Path $afterCapture.Screenshot
            }
        )
    }
} catch {
    $flowError = $_
} finally {
    try {
        Adb '-s' $Serial shell am force-stop $hostPackage | Out-Null
        Restore-Setting secure autofill_service $originalAutofill
        Adb '-s' $Serial shell cmd autofill reset | Out-Null
        Restore-Setting secure show_ime_with_hard_keyboard $originalHardKeyboard
        if (-not [string]::IsNullOrWhiteSpace($originalIme) -and $originalIme -ne 'null') {
            Adb '-s' $Serial shell ime enable $originalIme | Out-Null
            Adb '-s' $Serial shell ime set $originalIme | Out-Null
        }
        if (-not $providerWasInstalled) {
            Adb '-s' $Serial uninstall $providerPackage | Out-Null
        }
        if (-not $hostWasInstalled) {
            Adb '-s' $Serial uninstall $hostPackage | Out-Null
        }
        if (-not $imeWasInstalled) {
            Adb '-s' $Serial uninstall $imePackage | Out-Null
        }
    } catch {
        $restorationError = $_
    }
}

if ($null -ne $flowError) { throw $flowError }
if ($null -ne $restorationError) { throw $restorationError }

$restoredAutofill = ((Adb '-s' $Serial shell settings get secure autofill_service) -join '').Trim()
$restoredIme = ((Adb '-s' $Serial shell settings get secure default_input_method) -join '').Trim()
$restoredHardKeyboard = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
$restoration = [ordered]@{
    autofillServiceExact = $restoredAutofill -eq $originalAutofill
    defaultImeExact = $restoredIme -eq $originalIme
    showImeWithHardKeyboardExact = $restoredHardKeyboard -eq $originalHardKeyboard
    providerPresenceExact = (Test-PackageInstalled $providerPackage) -eq $providerWasInstalled
    hostPresenceExact = (Test-PackageInstalled $hostPackage) -eq $hostWasInstalled
    imePresenceExact = (Test-PackageInstalled $imePackage) -eq $imeWasInstalled
}
if (@($restoration.GetEnumerator() | Where-Object { -not $_.Value }).Count -ne 0) {
    throw "Inline-autofill fixture restoration was not exact: " +
        ($restoration | ConvertTo-Json -Compress)
}
$flowResult.restoration = $restoration
$flowResult | ConvertTo-Json -Depth 10 |
    Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'summary.json')
Write-Host "Deterministic platform inline-autofill flow passed: $OutputDirectory"
