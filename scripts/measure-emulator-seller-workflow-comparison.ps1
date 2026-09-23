[CmdletBinding()]
param(
    [string]$Serial = "",
    [string]$OutputDirectory = "",
    [ValidateRange(1, 40)][int]$ScenarioLimit = 40,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
} else {
    "adb"
}
$gradle = Join-Path $root "gradlew.bat"
$fixturePath = Join-Path $root "benchmark-fixtures\seller-workflow-scenarios.json"
$sellerPackage = "com.terasumi.sellerkeyboard"
$sellerIme = "$sellerPackage/.service.SellerKeyboard"
$gboardPackage = "com.google.android.inputmethod.latin"
$gboardIme = "$gboardPackage/com.android.inputmethod.latin.LatinIME"
$hostPackage = "com.terasumi.sellerkeyboard.benchmarkhost"
$hostActivity = "$hostPackage/.ImeBenchmarkActivity"
$hostBridge = "content://$hostPackage.host-test-bridge"
$imeBridge = "content://$sellerPackage.ime-test-bridge"
$touchPacingMillis = 80

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $root `
        "docs\evidence\verification\seller-workflow-comparison\latest"
} else {
    $OutputDirectory = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath(
        $OutputDirectory
    )
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$windowRoot = Join-Path $OutputDirectory "windows"
New-Item -ItemType Directory -Force -Path $windowRoot | Out-Null

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: $($Arguments -join ' ')`n$($result -join "`n")"
    }
    $result
}

function Bundle-Value([string]$Bundle, [string]$Key) {
    $match = [regex]::Match(
        $Bundle,
        "(?:\{|,\s*)$([regex]::Escape($Key))=([^,}]*)"
    )
    if (-not $match.Success) { return $null }
    $match.Groups[1].Value.Trim()
}

function Call-Bridge([string]$Uri, [string]$Method, [string]$Argument = "") {
    $arguments = @(
        '-s', $Serial, 'shell', 'content', 'call', '--uri', $Uri, '--method', $Method
    )
    if (-not [string]::IsNullOrWhiteSpace($Argument)) {
        $arguments += @('--arg', $Argument)
    }
    ((Adb @arguments) -join "`n").Trim()
}

function Sha256([string]$Value) {
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        -join ($algorithm.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') })
    } finally {
        $algorithm.Dispose()
    }
}

function Await-HostHash([string]$ExpectedHash, [string]$Name, [int]$Seconds = 12) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $snapshot = Call-Bridge $hostBridge 'snapshot'
        if ((Bundle-Value $snapshot 'active') -eq 'true' -and
            (Bundle-Value $snapshot 'text_sha256') -eq $ExpectedHash) {
            return $snapshot
        }
        Start-Sleep -Milliseconds 50
    } while ((Get-Date) -lt $deadline)
    $diagnostic = Call-Bridge $hostBridge 'seller_benchmark_snapshot'
    throw "Timed out waiting for host draft '$Name'. Last: $diagnostic"
}

function Seed-EmptyEditor([string]$Name) {
    $seed = Call-Bridge $hostBridge 'seed_seller_benchmark_empty'
    if ((Bundle-Value $seed 'seeded') -ne 'true') {
        throw "Neutral editor did not seed '$Name': $seed"
    }
    Await-HostHash (Sha256 '') "$Name-empty" | Out-Null
    # The editor state becomes empty before InputMethodManager finishes
    # reattaching the restarted input connection. Keep this setup wait outside
    # the measured interval so the first user-equivalent touch is not dropped.
    Start-Sleep -Milliseconds 350
}

function Start-Host {
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $hostActivity | Out-Null
    $deadline = (Get-Date).AddSeconds(20)
    do {
        $snapshot = Call-Bridge $hostBridge 'snapshot'
        if ((Bundle-Value $snapshot 'active') -eq 'true') { return }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)
    throw "Neutral benchmark editor did not become active. Last: $snapshot"
}

function Set-Ime([string]$Ime) {
    Adb '-s' $Serial shell ime enable $Ime | Out-Null
    Adb '-s' $Serial shell ime set $Ime | Out-Null
    Start-Host
    Start-Sleep -Milliseconds 400
}

function Dump-Window([string]$Name, [switch]$Screenshot) {
    $safeName = $Name -replace '[^a-zA-Z0-9_-]', '-'
    $remoteXml = "/sdcard/seller-benchmark-$safeName.xml"
    $localXml = Join-Path $windowRoot "$safeName.xml"
    Adb '-s' $Serial shell uiautomator dump '--windows' $remoteXml | Out-Null
    Adb '-s' $Serial pull '-q' $remoteXml $localXml | Out-Null
    if ($Screenshot) {
        $remotePng = "/sdcard/seller-benchmark-$safeName.png"
        $localPng = Join-Path $OutputDirectory "$safeName.png"
        Adb '-s' $Serial shell screencap '-p' $remotePng | Out-Null
        Adb '-s' $Serial pull '-q' $remotePng $localPng | Out-Null
    }
    [pscustomobject]@{
        Path = $localXml
        Xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $localXml
    }
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

function Await-Node([string[]]$Values, [string]$Name, [int]$Seconds = 15) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $window = Dump-Window $Name
        $node = Find-Node $window.Xml $Values
        if ($null -ne $node) {
            return [pscustomobject]@{ Node = $node; Window = $window }
        }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for '$($Values -join "' or '")' in $($window.Path)"
}

function Tap-Match([System.Text.RegularExpressions.Match]$Node) {
    $left = [int]$Node.Groups[1].Value
    $top = [int]$Node.Groups[2].Value
    $right = [int]$Node.Groups[3].Value
    $bottom = [int]$Node.Groups[4].Value
    $x = [int](($left + $right) / 2)
    $y = [int](($top + $bottom) / 2)
    Adb '-s' $Serial shell input tap $x $y | Out-Null
    Start-Sleep -Milliseconds $touchPacingMillis
}

function Point-FromMatch([System.Text.RegularExpressions.Match]$Node) {
    $left = [int]$Node.Groups[1].Value
    $top = [int]$Node.Groups[2].Value
    $right = [int]$Node.Groups[3].Value
    $bottom = [int]$Node.Groups[4].Value
    [pscustomobject]@{
        X = [int](($left + $right) / 2)
        Y = [int](($top + $bottom) / 2)
    }
}

function Tap-Node([string[]]$Values, [string]$Name) {
    $found = Await-Node $Values $Name
    Tap-Match $found.Node
}

function Get-AlphabetKeyMap([string]$Name) {
    $deadline = (Get-Date).AddSeconds(15)
    do {
        $window = Dump-Window $Name
        $map = @{}
        $matches = [regex]::Matches(
            $window.Xml,
            '<node[^>]*content-desc="([a-z]|Space)"[^>]*' +
                'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*/?>'
        )
        foreach ($match in $matches) {
            $left = [int]$match.Groups[2].Value
            $top = [int]$match.Groups[3].Value
            $right = [int]$match.Groups[4].Value
            $bottom = [int]$match.Groups[5].Value
            $map[$match.Groups[1].Value] = [pscustomobject]@{
                X = [int](($left + $right) / 2)
                Y = [int](($top + $bottom) / 2)
            }
        }
        if ($map.Count -eq 27) { return $map }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)
    throw "Expected 26 letters plus Space for '$Name'; found $($map.Count) in $($window.Path)"
}

function Invoke-KeySequence([string]$Value, [hashtable]$KeyMap) {
    $commands = [System.Collections.Generic.List[string]]::new()
    $commands.Add('sleep 0.25')
    foreach ($character in $Value.ToCharArray()) {
        $key = if ($character -eq ' ') { 'Space' } else { [string]$character }
        $point = $KeyMap[$key]
        if ($null -eq $point) { throw "Keyboard is missing key '$key'" }
        $commands.Add("input tap $($point.X) $($point.Y)")
        $commands.Add("sleep $($touchPacingMillis / 1000.0)")
    }
    Adb '-s' $Serial shell ($commands -join '; ') | Out-Null
}

function Invoke-InImeShortcut([string]$Shortcut, [string]$Name) {
    $window = (Await-Node @('Text entry key /') "$Name-keypad").Window
    $commands = [System.Collections.Generic.List[string]]::new()
    foreach ($character in $Shortcut.ToCharArray()) {
        $description = "Text entry key $character"
        $node = Find-Node $window.Xml @($description)
        if ($null -eq $node) { throw "In-IME keypad is missing '$description' in $($window.Path)" }
        $left = [int]$node.Groups[1].Value
        $top = [int]$node.Groups[2].Value
        $right = [int]$node.Groups[3].Value
        $bottom = [int]$node.Groups[4].Value
        $commands.Add("input tap $([int](($left + $right) / 2)) $([int](($top + $bottom) / 2))")
        $commands.Add("sleep $($touchPacingMillis / 1000.0)")
    }
    Adb '-s' $Serial shell ($commands -join '; ') | Out-Null
    Tap-Node @('Finish text entry') "$Name-finish-search"
}

function Get-CustomizedControlMap([object]$Scenario) {
    Seed-EmptyEditor 'seller-customized-warmup'
    $replies = Await-Node @('Snippet List', 'Snippets') 'seller-customized-warmup-replies'
    $repliesPoint = Point-FromMatch $replies.Node
    Tap-Match $replies.Node
    $search = Await-Node @('Search seller replies and commands') `
        'seller-customized-warmup-search'
    $searchPoint = Point-FromMatch $search.Node
    Tap-Match $search.Node
    $keypad = (Await-Node @('Text entry key /') `
        'seller-customized-warmup-keypad').Window
    $shortcutPoints = @{}
    foreach ($character in @('/', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
        'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z')) {
        $node = Find-Node $keypad.Xml @("Text entry key $character")
        if ($null -eq $node) {
            throw "Warm-up keypad is missing '$character' in $($keypad.Path)"
        }
        $shortcutPoints[[string]$character] = Point-FromMatch $node
    }
    $finishNode = Find-Node $keypad.Xml @('Finish text entry')
    if ($null -eq $finishNode) {
        throw "Warm-up keypad is missing its Finish action in $($keypad.Path)"
    }
    $finishPoint = Point-FromMatch $finishNode
    Invoke-KeySequence $Scenario.shortcut $shortcutPoints
    Tap-Match $finishNode
    $result = Await-Node @("Reply $($Scenario.title)") `
        'seller-customized-warmup-result'
    $resultPoint = Point-FromMatch $result.Node
    Tap-Match $result.Node
    $insert = Await-Node @('Insert reviewed reply') 'seller-customized-warmup-insert'
    $insertPoint = Point-FromMatch $insert.Node
    Seed-EmptyEditor 'seller-customized-warmup-reset'
    [pscustomobject]@{
        Replies = $repliesPoint
        Search = $searchPoint
        ShortcutKeys = $shortcutPoints
        Finish = $finishPoint
        Result = $resultPoint
        Insert = $insertPoint
    }
}

function Invoke-CustomizedSequence([string]$Shortcut, [pscustomobject]$Controls) {
    $commands = [System.Collections.Generic.List[string]]::new()
    $commands.Add('sleep 0.25')
    $commands.Add("input tap $($Controls.Replies.X) $($Controls.Replies.Y)")
    # Surface changes are Compose transitions. Keep their settling time inside
    # the user-equivalent measurement, without using untimed UI dumps.
    $commands.Add('sleep 0.75')
    $commands.Add("input tap $($Controls.Search.X) $($Controls.Search.Y)")
    $commands.Add('sleep 0.75')
    foreach ($character in $Shortcut.ToCharArray()) {
        $point = $Controls.ShortcutKeys[[string]$character]
        if ($null -eq $point) { throw "Warm-up map is missing shortcut key '$character'" }
        $commands.Add("input tap $($point.X) $($point.Y)")
        $commands.Add("sleep $($touchPacingMillis / 1000.0)")
    }
    $commands.Add("input tap $($Controls.Finish.X) $($Controls.Finish.Y)")
    $commands.Add('sleep 0.75')
    $commands.Add("input tap $($Controls.Result.X) $($Controls.Result.Y)")
    $commands.Add('sleep 0.75')
    $commands.Add("input tap $($Controls.Insert.X) $($Controls.Insert.Y)")
    Adb '-s' $Serial shell ($commands -join '; ') | Out-Null
}

function Start-Video([string]$Mode) {
    $remote = "/sdcard/seller-workflow-$Mode.mp4"
    Adb '-s' $Serial shell rm '-f' $remote | Out-Null
    $process = Start-Process -FilePath $adb -ArgumentList @(
        '-s', $Serial, 'shell', 'screenrecord', '--bit-rate', '2000000',
        '--time-limit', '180', $remote
    ) -WindowStyle Hidden -PassThru
    Start-Sleep -Milliseconds 500
    [pscustomobject]@{ Process = $process; Remote = $remote; Mode = $Mode }
}

function Stop-Video([pscustomobject]$Recording) {
    @(& $adb -s $Serial shell pkill '-2' screenrecord 2>&1) | Out-Null
    if (-not $Recording.Process.WaitForExit(10000)) {
        Stop-Process -Id $Recording.Process.Id -Force -ErrorAction SilentlyContinue
    }
    $local = Join-Path $OutputDirectory "$($Recording.Mode).mp4"
    Adb '-s' $Serial pull '-q' $Recording.Remote $local | Out-Null
    $local
}

function Median([double[]]$Values) {
    $sorted = @($Values | Sort-Object)
    $middle = [int]($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 0) {
        ([double]$sorted[$middle - 1] + [double]$sorted[$middle]) / 2.0
    } else {
        [double]$sorted[$middle]
    }
}

function Measure-TypingMode(
    [string]$Mode,
    [string]$Ime,
    [object[]]$Scenarios
) {
    Set-Ime $Ime
    Seed-EmptyEditor "$Mode-map"
    $keyMap = Get-AlphabetKeyMap "$Mode-key-map"
    Dump-Window "$Mode-start" -Screenshot | Out-Null
    $recording = $null
    $videos = @()
    $measurements = @()
    $scenarioIndex = 0
    try {
        foreach ($scenario in $Scenarios) {
            if ($scenarioIndex % 10 -eq 0) {
                if ($null -ne $recording) {
                    $videos += Stop-Video $recording
                }
                $part = [int]($scenarioIndex / 10) + 1
                $recording = Start-Video "$Mode-part$part"
            }
            Seed-EmptyEditor "$Mode-$($scenario.id)"
            $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
            Invoke-KeySequence $scenario.body $keyMap
            $snapshot = Await-HostHash (Sha256 $scenario.body) "$Mode-$($scenario.id)"
            $stopwatch.Stop()
            $measurements += [pscustomobject]@{
                id = $scenario.id
                category = $scenario.category
                mode = $Mode
                interactions = $scenario.body.Length
                elapsedMilliseconds = [math]::Round($stopwatch.Elapsed.TotalMilliseconds, 3)
                expectedSha256 = (Sha256 $scenario.body)
                actualSha256 = Bundle-Value $snapshot 'text_sha256'
                textLength = [int](Bundle-Value $snapshot 'text_length')
                completed = $true
            }
            $measurements | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 `
                (Join-Path $OutputDirectory "$Mode-measurements.checkpoint.json")
            Write-Host "$Mode $($scenario.id)/$($Scenarios.Count) passed"
            $scenarioIndex++
        }
    } finally {
        if ($null -ne $recording) {
            $videos += Stop-Video $recording
        }
    }
    Dump-Window "$Mode-end" -Screenshot | Out-Null
    [pscustomobject]@{ Measurements = $measurements; Videos = $videos }
}

function Measure-CustomizedMode([object[]]$Scenarios) {
    $mode = 'seller-customized'
    Set-Ime $sellerIme
    $controls = Get-CustomizedControlMap $Scenarios[0]
    Dump-Window "$mode-start" -Screenshot | Out-Null
    $recording = $null
    $videos = @()
    $measurements = @()
    $scenarioIndex = 0
    try {
        foreach ($scenario in $Scenarios) {
            if ($scenarioIndex % 10 -eq 0) {
                if ($null -ne $recording) {
                    $videos += Stop-Video $recording
                }
                $part = [int]($scenarioIndex / 10) + 1
                $recording = Start-Video "$mode-part$part"
            }
            Seed-EmptyEditor "$mode-$($scenario.id)"
            $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
            Invoke-CustomizedSequence $scenario.shortcut $controls
            $snapshot = Await-HostHash (Sha256 $scenario.body) "$mode-$($scenario.id)"
            $stopwatch.Stop()
            $measurements += [pscustomobject]@{
                id = $scenario.id
                category = $scenario.category
                mode = $mode
                interactions = $scenario.shortcut.Length + 5
                elapsedMilliseconds = [math]::Round($stopwatch.Elapsed.TotalMilliseconds, 3)
                expectedSha256 = (Sha256 $scenario.body)
                actualSha256 = Bundle-Value $snapshot 'text_sha256'
                textLength = [int](Bundle-Value $snapshot 'text_length')
                completed = $true
            }
            $measurements | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 `
                (Join-Path $OutputDirectory "$mode-measurements.checkpoint.json")
            Write-Host "$mode $($scenario.id)/$($Scenarios.Count) passed"
            $scenarioIndex++
        }
    } finally {
        if ($null -ne $recording) {
            $videos += Stop-Video $recording
        }
    }
    Dump-Window "$mode-end" -Screenshot | Out-Null
    [pscustomobject]@{ Measurements = $measurements; Videos = $videos }
}

$deviceRows = @(& $adb devices | Where-Object { $_ -match '^emulator-\d+\s+device$' })
if ([string]::IsNullOrWhiteSpace($Serial)) {
    if ($deviceRows.Count -ne 1) {
        throw "Specify -Serial unless exactly one Android emulator is connected."
    }
    $Serial = ($deviceRows[0] -split '\s+')[0]
}
if (-not ($deviceRows | Where-Object { ($_ -split '\s+')[0] -eq $Serial })) {
    throw "Emulator '$Serial' is not connected."
}
if (((Adb '-s' $Serial shell getprop ro.kernel.qemu) -join '').Trim() -ne '1') {
    throw "This runner is intentionally scoped to an Android emulator."
}
if (-not (Test-Path -LiteralPath $fixturePath)) {
    throw "Seller workflow fixture is missing: $fixturePath"
}
$scenarios = @(
    Get-Content -Raw -Encoding UTF8 -LiteralPath $fixturePath |
        ConvertFrom-Json |
        Select-Object -First $ScenarioLimit
)
if ($scenarios.Count -ne $ScenarioLimit) {
    throw "Expected $ScenarioLimit scenarios; loaded $($scenarios.Count)."
}

if (-not $SkipBuild) {
    & $gradle ':app:sellerWorkflowBenchmark' ':app:assembleDebug' `
        ':benchmark-host:assembleBenchmark' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw "Seller workflow comparison build failed." }
}
Adb '-s' $Serial install '-r' `
    (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk') | Out-Null
Adb '-s' $Serial install '-r' `
    (Join-Path $root 'benchmark-host\build\outputs\apk\benchmark\benchmark-host-benchmark.apk') |
    Out-Null

$gboardPackageDump = (Adb '-s' $Serial shell dumpsys package $gboardPackage) -join "`n"
$gboardVersionName = [regex]::Match($gboardPackageDump, 'versionName=([^\s]+)')
$gboardVersionCode = [regex]::Match($gboardPackageDump, 'versionCode=(\d+)')
if (-not $gboardVersionName.Success -or -not $gboardVersionCode.Success) {
    throw "Gboard is not installed or its version could not be resolved on $Serial."
}

$originalIme = ((Adb '-s' $Serial shell settings get secure default_input_method) -join '').Trim()
$originalShowIme = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
$seeded = $false
try {
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Set-Ime $sellerIme
    $seed = Call-Bridge $imeBridge 'seed_seller_benchmark'
    if ((Bundle-Value $seed 'seller_benchmark_seeded') -ne 'true' -or
        [int](Bundle-Value $seed 'seller_benchmark_template_count') -ne 40) {
        throw "Seller benchmark templates were not seeded: $seed"
    }
    $seeded = $true

    Set-Ime $gboardIme
    $gboardSelectedSubtype = ((Adb '-s' $Serial shell settings get secure `
        selected_input_method_subtype) -join '').Trim()
    $inputMethodDump = (Adb '-s' $Serial shell dumpsys input_method) -join "`n"
    $inputMethodDump | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'input-method-configuration.txt')

    $gboardRun = Measure-TypingMode 'gboard' $gboardIme $scenarios
    $sellerDefaultRun = Measure-TypingMode 'seller-default' $sellerIme $scenarios
    $sellerCustomizedRun = Measure-CustomizedMode $scenarios
    $allMeasurements = @(
        $gboardRun.Measurements +
        $sellerDefaultRun.Measurements +
        $sellerCustomizedRun.Measurements
    )
    $gboardInteractionMedian = Median @(
        $gboardRun.Measurements | ForEach-Object { [double]$_.interactions }
    )
    $sellerDefaultInteractionMedian = Median @(
        $sellerDefaultRun.Measurements | ForEach-Object { [double]$_.interactions }
    )
    $sellerCustomizedInteractionMedian = Median @(
        $sellerCustomizedRun.Measurements | ForEach-Object { [double]$_.interactions }
    )
    $gboardTimeMedian = Median @(
        $gboardRun.Measurements | ForEach-Object { [double]$_.elapsedMilliseconds }
    )
    $sellerDefaultTimeMedian = Median @(
        $sellerDefaultRun.Measurements | ForEach-Object { [double]$_.elapsedMilliseconds }
    )
    $sellerCustomizedTimeMedian = Median @(
        $sellerCustomizedRun.Measurements | ForEach-Object { [double]$_.elapsedMilliseconds }
    )
    $interactionReduction = 1.0 -
        ($sellerCustomizedInteractionMedian / $gboardInteractionMedian)
    $timeReduction = 1.0 - ($sellerCustomizedTimeMedian / $gboardTimeMedian)
    $isFullRun = $ScenarioLimit -eq 40
    if ($isFullRun -and $interactionReduction -lt 0.50) {
        throw "Measured interaction reduction $interactionReduction is below the 50% gate."
    }

    $allMeasurements | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'measurements.json')
    $locale = ((Adb '-s' $Serial shell getprop persist.sys.locale) -join '').Trim()
    if ([string]::IsNullOrWhiteSpace($locale)) {
        $locale = ((Adb '-s' $Serial shell settings get system system_locales) -join '').Trim()
    }
    if ([string]::IsNullOrWhiteSpace($locale) -or $locale -eq 'null') {
        $locale = ((Adb '-s' $Serial shell getprop ro.product.locale) -join '').Trim()
    }
    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = if ($isFullRun) { 'PASS' } else { 'PARTIAL' }
        evidenceClass = 'same-emulator real-touch comparison; not human or physical-device timing'
        serial = $Serial
        manufacturer = ((Adb '-s' $Serial shell getprop ro.product.manufacturer) -join '').Trim()
        model = ((Adb '-s' $Serial shell getprop ro.product.model) -join '').Trim()
        api = [int]((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '').Trim()
        android = ((Adb '-s' $Serial shell getprop ro.build.version.release) -join '').Trim()
        buildFingerprint = ((Adb '-s' $Serial shell getprop ro.build.fingerprint) -join '').Trim()
        locale = $locale
        gboardPackage = $gboardPackage
        gboardVersionName = $gboardVersionName.Groups[1].Value
        gboardVersionCode = [long]$gboardVersionCode.Groups[1].Value
        gboardSelectedSubtype = $gboardSelectedSubtype
        gboardSettingsBoundary = 'Private Gboard preferences are not shell-readable; public package version, selected subtype, surface, and input-method dump are captured.'
        sellerPackage = $sellerPackage
        scenarioFixture = 'benchmark-fixtures/seller-workflow-scenarios.json'
        scenarioCount = $ScenarioLimit
        touchPacingMillis = $touchPacingMillis
        touchMethod = 'real accessibility-resolved key coordinates injected with input tap'
        draftVerification = 'distinct-package editor SHA-256 after every scenario'
        gboardMedianInteractions = $gboardInteractionMedian
        sellerDefaultMedianInteractions = $sellerDefaultInteractionMedian
        sellerCustomizedMedianInteractions = $sellerCustomizedInteractionMedian
        interactionReduction = $interactionReduction
        interactionGateMinimum = 0.50
        gboardMedianMilliseconds = $gboardTimeMedian
        sellerDefaultMedianMilliseconds = $sellerDefaultTimeMedian
        sellerCustomizedMedianMilliseconds = $sellerCustomizedTimeMedian
        customizedTimeReductionVsGboard = $timeReduction
        completedDrafts = @($allMeasurements | Where-Object completed).Count
        expectedDrafts = $ScenarioLimit * 3
        autoSentMessages = 0
        unresolvedVariablesCommitted = 0
        videos = [ordered]@{
            gboard = $gboardRun.Videos
            sellerDefault = $sellerDefaultRun.Videos
            sellerCustomized = $sellerCustomizedRun.Videos
        }
    } | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'summary.json')

    Write-Host "Seller workflow same-emulator comparison passed: $OutputDirectory"
} finally {
    if ($seeded) {
        Set-Ime $sellerIme
        $restore = Call-Bridge $imeBridge 'restore_seller_benchmark'
        if ((Bundle-Value $restore 'seller_benchmark_restored') -ne 'true') {
            throw "Seller benchmark templates were not restored: $restore"
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($originalIme) -and $originalIme -ne 'null') {
        Adb '-s' $Serial shell ime set $originalIme | Out-Null
    }
    if ([string]::IsNullOrWhiteSpace($originalShowIme) -or $originalShowIme -eq 'null') {
        Adb '-s' $Serial shell settings delete secure show_ime_with_hard_keyboard | Out-Null
    } else {
        Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard `
            $originalShowIme | Out-Null
    }
}
