[CmdletBinding()]
param(
    [string]$ApprovedExceptionPath = "",
    [string]$ProductDecisionApprovalPath = "",
    [string]$DeviceSerial = "",
    [switch]$EvaluateReleaseReadinessOnly,
    [switch]$ReuseCompletedMacrobenchmark,
    [switch]$MacrobenchmarkOnly
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$reportRoot = Join-Path $repositoryRoot "docs\evidence\verification\latest"
$gradle = Join-Path $repositoryRoot "gradlew.bat"
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
} else {
    "adb"
}
$results = [System.Collections.Generic.List[object]]::new()

New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null

function Add-Result {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail
    )

    $results.Add([pscustomobject]@{
        name = $Name
        status = $Status
        detail = $Detail
    })
}

function Write-ConnectedSkipReport {
    param([object[]]$SkippedResults)

    $legacySkipLog = Join-Path $reportRoot 'connected-test-skips.txt'
    if (Test-Path -LiteralPath $legacySkipLog) {
        Remove-Item -LiteralPath $legacySkipLog -Force
    }
    $skipLog = Join-Path $reportRoot 'connected-test-skips.json'
    @($SkippedResults) | ConvertTo-Json -Depth 4 |
        Set-Content -Encoding utf8 $skipLog
    return $skipLog
}

function Invoke-GradleGate {
    param(
        [string]$Name,
        [string[]]$Tasks,
        [switch]$AllowFailure
    )

    $safeName = $Name.ToLowerInvariant().Replace(" ", "-")
    $logPath = Join-Path $reportRoot "$safeName.log"
    Write-Host "`n== $Name =="
    # Gradle legitimately writes diagnostics to stderr. Windows PowerShell turns
    # native stderr into non-terminating ErrorRecord objects; with the script's
    # fail-fast preference those records can abort before the exit code and gate
    # result are saved. Defer failure handling to the native exit code here.
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $gradle @Tasks --console=plain 2>&1 |
            Tee-Object -FilePath $logPath |
            Write-Host
        $gradleExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if ($gradleExitCode -ne 0) {
        if ($AllowFailure) {
            return $false
        }
        Add-Result -Name $Name -Status "FAIL" -Detail $logPath
        throw "$Name failed. See $logPath"
    }
    Add-Result -Name $Name -Status "PASS" -Detail $logPath
    if ($AllowFailure) {
        return $true
    }
}

function Invoke-AdbCommand {
    param(
        [string]$Name,
        [string[]]$Arguments,
        [string]$LogPath
    )

    # ADB writes successful install/pull progress to stderr. Windows PowerShell
    # promotes that stream to ErrorRecord under Stop, so judge the native exit
    # code explicitly after preserving the combined log.
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $adb @Arguments 2>&1 | Tee-Object -FilePath $LogPath -Append | Write-Host
        $adbExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if ($adbExitCode -ne 0) {
        throw "$Name failed. See $LogPath"
    }
}

function Invoke-DirectConnectedTests {
    param([string]$Serial)

    $directLog = Join-Path $reportRoot "connected-android-tests-direct.log"
    "Direct package install for serial=$Serial" | Set-Content -Encoding utf8 $directLog
    Invoke-AdbCommand -Name "Direct debug APK install" -Arguments @(
        '-s', $Serial, 'install', '-r',
        (Join-Path $repositoryRoot 'app\build\outputs\apk\debug\app-debug.apk')
    ) -LogPath $directLog
    Invoke-AdbCommand -Name "Direct test APK install" -Arguments @(
        '-s', $Serial, 'install', '-r',
        (Join-Path $repositoryRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
    ) -LogPath $directLog
    Invoke-AdbCommand -Name "Direct neutral benchmark host install" -Arguments @(
        '-s', $Serial, 'install', '-r',
        (Join-Path $repositoryRoot `
            'benchmark-host\build\outputs\apk\benchmark\benchmark-host-benchmark.apk')
    ) -LogPath $directLog
    Invoke-AdbCommand -Name "Reset IME after direct package replacement" -Arguments @(
        '-s', $Serial, 'shell', 'ime', 'reset'
    ) -LogPath $directLog
    Invoke-AdbCommand -Name "Enable SellerKeyboard after direct package replacement" `
        -Arguments @(
            '-s', $Serial, 'shell', 'ime', 'enable',
            'com.terasumi.sellerkeyboard/.service.SellerKeyboard'
        ) -LogPath $directLog
    Invoke-AdbCommand -Name "Select SellerKeyboard after direct package replacement" `
        -Arguments @(
            '-s', $Serial, 'shell', 'ime', 'set',
            'com.terasumi.sellerkeyboard/.service.SellerKeyboard'
        ) -LogPath $directLog
    Start-Sleep -Milliseconds 500

    $runnerOutput = @(& $adb -s $Serial shell am instrument -w -r `
        'com.terasumi.sellerkeyboard.test/androidx.test.runner.AndroidJUnitRunner' 2>&1)
    $runnerExitCode = $LASTEXITCODE
    $runnerOutput | Tee-Object -FilePath $directLog -Append | Write-Host
    $runnerText = $runnerOutput -join "`n"
    $optionalSkips = ([regex]::Matches(
        $runnerText,
        'INSTRUMENTATION_STATUS_CODE: -4'
    )).Count
    if ($runnerExitCode -ne 0 -or $runnerText -notmatch 'OK \(22 tests\)' -or
        $runnerText -match 'FAILURES!!!' -or $optionalSkips -ne 2 -or
        $runnerText -notmatch 'ConvexBackupConnectedSmokeTest'
    ) {
        throw "Direct connected tests did not produce 22 passing/identified results. See $directLog"
    }
    $isEmulator = ((& $adb -s $Serial shell getprop ro.kernel.qemu) -join '').Trim() -eq '1'
    $connectedStatus = if ($isEmulator) { 'PASS_EMULATOR' } else { 'PASS_OEM_FALLBACK' }
    Add-Result -Name "Connected Android tests" -Status $connectedStatus -Detail $directLog
    $optionalReason =
        'Optional live backend smoke test requires instrumentation argument convexSmoke=true.'
    $directSkips = @(
        [pscustomobject]@{
            ResultPath = [System.IO.Path]::GetRelativePath($repositoryRoot, $directLog)
            ClassName = 'com.terasumi.sellerkeyboard.backend.ConvexBackupConnectedSmokeTest'
            TestName = 'registerBackupRestoreAndKeyboardSnippetInsert'
            Reason = $optionalReason
        }
        [pscustomobject]@{
            ResultPath = [System.IO.Path]::GetRelativePath($repositoryRoot, $directLog)
            ClassName = 'com.terasumi.sellerkeyboard.backend.ConvexBackupConnectedSmokeTest'
            TestName = 'repositoryRegisterLoginBackupListAndDeleteWorksOnDevice'
            Reason = $optionalReason
        }
    )
    $skipLog = Write-ConnectedSkipReport -SkippedResults $directSkips
    Add-Result -Name "Optional live backend smoke tests" -Status "IDENTIFIED_SKIP" -Detail (
        "2 opt-in Convex tests; $skipLog"
    )
}

function Invoke-DirectMacrobenchmark {
    param([string]$Serial)

    $directLog = Join-Path $reportRoot "connected-macrobenchmark-direct.log"
    "Direct package install for serial=$Serial" | Set-Content -Encoding utf8 $directLog
    Invoke-AdbCommand -Name "Direct benchmark host install" -Arguments @(
        '-s', $Serial, 'install', '-r',
        (Join-Path $repositoryRoot 'benchmark-host\build\outputs\apk\benchmark\benchmark-host-benchmark.apk')
    ) -LogPath $directLog
    Invoke-AdbCommand -Name "Direct benchmark runner install" -Arguments @(
        '-s', $Serial, 'install', '-r',
        (Join-Path $repositoryRoot 'benchmark\build\outputs\apk\benchmark\benchmark-benchmark.apk')
    ) -LogPath $directLog

    $instrumentArguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
    $isEmulator = ((& $adb -s $Serial shell getprop ro.kernel.qemu) -join '').Trim() -eq '1'
    if ($isEmulator) {
        # AndroidX Benchmark rejects emulators by default. This explicit
        # suppression keeps the artifact correctly classified as emulator-only;
        # it does not promote it to physical-device performance evidence.
        $instrumentArguments += @('-e', 'androidx.benchmark.suppressErrors', 'EMULATOR')
    }
    $instrumentArguments += `
        'com.terasumi.sellerkeyboard.benchmark/androidx.test.runner.AndroidJUnitRunner'
    $runnerOutput = @(& $adb @instrumentArguments 2>&1)
    $runnerExitCode = $LASTEXITCODE
    $runnerOutput | Tee-Object -FilePath $directLog -Append | Write-Host
    "nativeAdbExitCode=$runnerExitCode" | Add-Content -Encoding utf8 $directLog
    $runnerText = Get-Content -Raw -LiteralPath $directLog
    if (-not (Test-CompletedMacrobenchmarkLog -Text $runnerText)) {
        throw "Direct Macrobenchmark did not produce all required metrics. See $directLog"
    }
    Copy-CompletedMacrobenchmarkJson -Serial $Serial -LogPath $directLog
    $macroStatus = if ($isEmulator) { 'PASS_EMULATOR' } else { 'PASS_OEM_FALLBACK' }
    Add-Result -Name "Connected Macrobenchmark" -Status $macroStatus -Detail $directLog
}

function Test-CompletedMacrobenchmarkLog {
    param([string]$Text)

    return $Text -match 'OK \(4 tests\)' -and
        $Text -match 'INSTRUMENTATION_CODE: -1' -and
        $Text -notmatch 'FAILURES!!!' -and
        $Text -match 'SellerKeyboard\.ImeShowToInteractiveFirstMs' -and
        $Text -match 'SellerKeyboard\.keyDispatchMaxMs' -and
        $Text -match 'SellerKeyboard\.candidateQueryMaxMs' -and
        $Text -match 'SellerKeyboard\.candidateDisplayMaxMs' -and
        $Text -match 'SellerKeyboard\.surfaceSwitchMaxMs' -and
        $Text -match 'SellerKeyboard\.templateInsertionMaxMs' -and
        $Text -match 'memoryRssAnonMaxKb'
}

function Copy-CompletedMacrobenchmarkJson {
    param([string]$Serial, [string]$LogPath)

    $benchmarkDestination = Join-Path $reportRoot "connected-macrobenchmark-output"
    New-Item -ItemType Directory -Force -Path $benchmarkDestination | Out-Null
    Invoke-AdbCommand -Name "Pull benchmark JSON" -Arguments @(
        '-s', $Serial, 'pull',
        '/storage/emulated/0/Android/media/com.terasumi.sellerkeyboard.benchmark/com.terasumi.sellerkeyboard.benchmark-benchmarkData.json',
        (Join-Path $benchmarkDestination 'benchmarkData.json')
    ) -LogPath $LogPath

    $remoteRoot = '/storage/emulated/0/Android/media/com.terasumi.sellerkeyboard.benchmark'
    $tracePattern = "$remoteRoot/ImeShowBenchmark_" +
        'productionKeyCandidateSurfaceAndTemplateFlowsHavePerfettoSections_' +
        'iter*.perfetto-trace'
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $remoteTraces = @(& $adb -s $Serial shell ls -1t $tracePattern 2>&1)
        $traceListExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    $remoteTraces = @(
        $remoteTraces | Where-Object { [string]$_ -match '\.perfetto-trace$' } |
            Select-Object -First 3
    )
    if ($traceListExitCode -ne 0 -or $remoteTraces.Count -ne 3) {
        throw 'The completed Macrobenchmark does not expose three production Perfetto traces.'
    }
    $traceDestination = Join-Path $benchmarkDestination 'production-traces'
    New-Item -ItemType Directory -Force -Path $traceDestination | Out-Null
    foreach ($remoteTrace in $remoteTraces) {
        if ([string]$remoteTrace -notmatch '_iter(\d{3})_') {
            throw "Could not parse production trace iteration: $remoteTrace"
        }
        $localTrace = Join-Path $traceDestination `
            "production-flow-iter$($Matches[1]).perfetto-trace"
        Invoke-AdbCommand -Name "Pull production Perfetto trace $($Matches[1])" -Arguments @(
            '-s', $Serial, 'pull', ([string]$remoteTrace), $localTrace
        ) -LogPath $LogPath
    }
}

function Test-MacrobenchmarkThresholds {
    param([string]$BenchmarkRoot)

    $dataPath = Get-ChildItem -Path $BenchmarkRoot -Recurse `
        -Filter '*benchmarkData.json' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $dataPath) {
        throw "Macrobenchmark JSON is missing under $BenchmarkRoot"
    }
    $data = Get-Content -Raw -LiteralPath $dataPath | ConvertFrom-Json
    $seller = $data.benchmarks | Where-Object {
        $_.name -eq 'coldEditorLaunchAndSellerKeyboardImeShow'
    }
    $gboard = $data.benchmarks | Where-Object {
        $_.name -eq 'coldEditorLaunchAndGboardImeShowReference'
    }
    $stress = $data.benchmarks | Where-Object {
        $_.name -eq 'oneHundredImeShowHideCyclesTrackFramesAndMemory'
    }
    $productionFlows = $data.benchmarks | Where-Object {
        $_.name -eq 'productionKeyCandidateSurfaceAndTemplateFlowsHavePerfettoSections'
    }
    if (-not $seller -or -not $gboard -or -not $stress -or -not $productionFlows) {
        throw "Macrobenchmark JSON does not contain all four required benchmarks: $dataPath"
    }
    $sellerMedian = [double]$seller.metrics.'SellerKeyboard.ImeShowToInteractiveFirstMs'.median
    $gboardMedian = [double]$gboard.metrics.'SellerKeyboard.ImeShowToInteractiveFirstMs'.median
    $ratio = $sellerMedian / $gboardMedian
    $sampledFrameMaximum = 0.0
    foreach ($run in $stress.sampledMetrics.frameDurationCpuMs.runs) {
        foreach ($sample in $run) {
            $sampledFrameMaximum = [Math]::Max($sampledFrameMaximum, [double]$sample)
        }
    }
    $productionTraceMetrics = [ordered]@{
        keyDispatchMaximumMs = [double]$productionFlows.metrics.'SellerKeyboard.keyDispatchMaxMs'.maximum
        candidateQueryMaximumMs = [double]$productionFlows.metrics.'SellerKeyboard.candidateQueryMaxMs'.maximum
        candidateDisplayMaximumMs = [double]$productionFlows.metrics.'SellerKeyboard.candidateDisplayMaxMs'.maximum
        surfaceSwitchMaximumMs = [double]$productionFlows.metrics.'SellerKeyboard.surfaceSwitchMaxMs'.maximum
        templateInsertionMaximumMs = [double]$productionFlows.metrics.'SellerKeyboard.templateInsertionMaxMs'.maximum
    }
    $traceRoot = Join-Path $BenchmarkRoot 'production-traces'
    $traceFiles = @(
        Get-ChildItem -LiteralPath $traceRoot -Filter 'production-flow-iter*.perfetto-trace' `
            -File -ErrorAction SilentlyContinue | Sort-Object Name
    )
    if ($traceFiles.Count -ne 3) {
        throw "Exactly three retained production Perfetto traces are required under $traceRoot"
    }
    $traceManifest = [ordered]@{
        benchmark = 'productionKeyCandidateSurfaceAndTemplateFlowsHavePerfettoSections'
        iterations = 3
        metricsMaximumMs = $productionTraceMetrics
        files = @(
            $traceFiles | ForEach-Object {
                [ordered]@{
                    path = $_.Name
                    bytes = $_.Length
                    sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash
                }
            }
        )
    }
    $traceManifest | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 `
        (Join-Path $traceRoot 'manifest.json')
    $thresholdReport = Join-Path $reportRoot 'macrobenchmark-thresholds.json'
    [ordered]@{
        sellerVisibleImeMedianMs = $sellerMedian
        gboardVisibleImeMedianMs = $gboardMedian
        sellerToGboardRatio = $ratio
        ratioLimit = 1.25
        stressSampledFrameMaximumMs = $sampledFrameMaximum
        frozenFrameLimitMs = 700.0
        stressHeapMaximumKb = [double]$stress.metrics.memoryHeapSizeMaxKb.maximum
        stressRssAnonMaximumKb = [double]$stress.metrics.memoryRssAnonMaxKb.maximum
        productionTraceSections = $productionTraceMetrics
        benchmarkData = $dataPath
    } | ConvertTo-Json | Set-Content -Encoding utf8 $thresholdReport
    $missingProductionTrace = @(
        $productionTraceMetrics.GetEnumerator() | Where-Object { [double]$_.Value -le 0.0 }
    )
    if ($ratio -gt 1.25 -or $sampledFrameMaximum -gt 700.0 -or
        $missingProductionTrace.Count -gt 0) {
        Add-Result -Name 'Macrobenchmark thresholds' -Status 'FAIL' -Detail $thresholdReport
        throw "Macrobenchmark performance threshold failed. See $thresholdReport"
    }
    Add-Result -Name 'Macrobenchmark thresholds' -Status 'PASS' -Detail $thresholdReport
}

function Invoke-PrivacyNetworkProbe {
    param([string]$Serial)

    $probeRoot = Join-Path $reportRoot 'privacy-network-probe'
    $probeScript = Join-Path $repositoryRoot 'scripts\capture-privacy-network-evidence.ps1'
    Write-Host "`n== Privacy/network typing probe =="
    & $probeScript -Serial $Serial -OutputDirectory $probeRoot
    if ($LASTEXITCODE -ne 0) {
        Add-Result -Name 'Privacy/network typing probe' -Status 'FAIL' -Detail $probeRoot
        throw "Privacy/network typing probe failed. See $probeRoot"
    }
    $summaryPath = Join-Path $probeRoot 'summary.json'
    if (-not (Test-Path -LiteralPath $summaryPath)) {
        Add-Result -Name 'Privacy/network typing probe' -Status 'FAIL' -Detail $probeRoot
        throw "Privacy/network probe summary is missing. See $probeRoot"
    }
    $probe = Get-Content -Raw -LiteralPath $summaryPath | ConvertFrom-Json
    if (-not $probe.networkAccountingSupported -or
        [long]$probe.rxDelta -ne 0 -or [long]$probe.txDelta -ne 0 -or
        [int]$probe.canaryOccurrencesInUidLog -ne 0) {
        Add-Result -Name 'Privacy/network typing probe' -Status 'FAIL' -Detail $summaryPath
        throw "Privacy/network probe thresholds failed. See $summaryPath"
    }
    Add-Result -Name 'Privacy/network typing probe' -Status 'PASS' -Detail $summaryPath
}

function Assert-SellerWorkflowComparisonEvidence {
    $comparisonRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\seller-workflow-comparison'
    $summaryFiles = @(
        Get-ChildItem -Path $comparisonRoot -Filter 'summary.json' -Recurse `
            -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending
    )
    $selected = $null
    foreach ($summaryFile in $summaryFiles) {
        $candidate = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
        if ($candidate.overall -eq 'PASS' -and [int]$candidate.scenarioCount -eq 40) {
            $selected = [pscustomobject]@{ File = $summaryFile; Data = $candidate }
            break
        }
    }
    if ($null -eq $selected) {
        Add-Result -Name 'Same-emulator seller workflow comparison' -Status 'FAIL' `
            -Detail $comparisonRoot
        throw 'A passing 40-scenario seller workflow comparison is missing.'
    }

    $summary = $selected.Data
    $evidenceRoot = $selected.File.DirectoryName
    $measurementsPath = Join-Path $evidenceRoot 'measurements.json'
    # Windows PowerShell 5.1 emits a JSON root array as one non-enumerated
    # pipeline object. Direct assignment preserves its 120 array elements;
    # wrapping the pipeline in @() incorrectly reports Count=1.
    $measurements = Get-Content -Raw -LiteralPath $measurementsPath | ConvertFrom-Json
    $exactMeasurements = @(
        $measurements | Where-Object {
            $_.completed -and $_.expectedSha256 -eq $_.actualSha256
        }
    )
    $videos = @(Get-ChildItem -Path $evidenceRoot -Filter '*.mp4' -File)
    $emptyVideos = @($videos | Where-Object { $_.Length -le 0 })
    $comparisonViolations = @(
        if ([int]$summary.completedDrafts -ne 120) { 'completedDrafts' }
        if ([int]$summary.expectedDrafts -ne 120) { 'expectedDrafts' }
        if ($measurements.Count -ne 120) { 'measurementCount' }
        if ($exactMeasurements.Count -ne 120) { 'exactMeasurementCount' }
        if ([double]$summary.interactionReduction -lt 0.50) { 'interactionReduction' }
        if ([double]$summary.gboardMedianMilliseconds -le 0) { 'gboardMedianMilliseconds' }
        if ([double]$summary.sellerCustomizedMedianMilliseconds -le 0) {
            'sellerCustomizedMedianMilliseconds'
        }
        if ([string]::IsNullOrWhiteSpace([string]$summary.gboardVersionName)) {
            'gboardVersionName'
        }
        if ([string]::IsNullOrWhiteSpace([string]$summary.gboardSelectedSubtype)) {
            'gboardSelectedSubtype'
        }
        if ($videos.Count -lt 12) { 'videoCount' }
        if ($emptyVideos.Count -gt 0) { 'emptyVideos' }
    )
    if ($comparisonViolations.Count -gt 0) {
        Add-Result -Name 'Same-emulator seller workflow comparison' -Status 'FAIL' `
            -Detail "$($comparisonViolations -join ', '); $($selected.File.FullName)"
        throw "Seller workflow comparison evidence violations: $($comparisonViolations -join ', ')"
    }
    Add-Result -Name 'Same-emulator seller workflow comparison' -Status 'PASS' -Detail (
        "120/120 exact drafts; interaction reduction " +
        "$([math]::Round([double]$summary.interactionReduction * 100, 2))%; " +
        "Gboard $($summary.gboardVersionName); $($selected.File.FullName)"
    )
}

function Assert-NamedAppEditorEvidence {
    $evidenceRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-named-apps'
    $summaryFile = Get-ChildItem -Path $evidenceRoot -Filter 'summary.json' -Recurse `
        -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $summaryFile) {
        throw "Named-app editor evidence is missing under $evidenceRoot"
    }
    $summary = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
    $privacySummary = Join-Path $summaryFile.DirectoryName `
        $summary.privacy.attributionSummary
    $privacyUidLog = Join-Path $summaryFile.DirectoryName $summary.privacy.uidLog
    $privacyNetstats = Join-Path $summaryFile.DirectoryName $summary.privacy.uidNetstats
    if ($summary.overall -ne 'PASS' -or [int]$summary.api -ne 36 -or
        $summary.accountUsed -or $summary.submitted -or
        -not $summary.chrome.requiredByGoal -or
        $summary.chrome.editorClass -ne 'URL/search' -or
        -not $summary.chrome.exactDraftVerified -or
        [int]$summary.chrome.imeKeyTapCount -ne 5 -or
        -not $summary.chrome.imeSession.inputShown -or
        $summary.chrome.imeSession.targetPackage -ne 'com.android.chrome' -or
        $summary.messages.editorClass -ne 'search + SMS compose' -or
        -not $summary.messages.exactDraftVerified -or
        [int]$summary.messages.imeKeyTapCount -ne 17 -or
        -not $summary.messages.search.exactDraftVerified -or
        [int]$summary.messages.search.imeKeyTapCount -ne 5 -or
        -not $summary.messages.search.imeSession.inputShown -or
        $summary.messages.search.imeSession.targetPackage -ne 'com.google.android.apps.messaging' -or
        $summary.messages.compose.fictionalRecipient -ne '5550100' -or
        $summary.messages.compose.formattedRecipient -ne '555-0100' -or
        $summary.messages.compose.expectedDraft -ne 'Skqvz' -or
        -not $summary.messages.compose.exactDraftVerified -or
        $summary.messages.compose.submitted -or
        [int]$summary.messages.compose.recipientImeKeyTapCount -ne 7 -or
        [int]$summary.messages.compose.draftImeKeyTapCount -ne 5 -or
        [int]$summary.messages.compose.recipientSemanticNodeCount -lt 26 -or
        [int]$summary.messages.compose.composerSemanticNodeCount -lt 26 -or
        -not $summary.messages.compose.recipientImeSession.inputShown -or
        $summary.messages.compose.recipientImeSession.targetPackage -ne 'com.google.android.apps.messaging' -or
        -not $summary.messages.compose.composerImeSession.inputShown -or
        $summary.messages.compose.composerImeSession.targetPackage -ne 'com.google.android.apps.messaging' -or
        -not $summary.messages.compose.sendControlPresent -or
        $summary.messages.compose.imeActionLabel -ne 'Send' -or
        -not $summary.messages.productConfirmationPending -or
        -not $summary.privacy.namedTargetsVerified -or
        @($summary.privacy.targetPackages).Count -ne 2 -or
        @($summary.privacy.targetPackages) -notcontains 'com.android.chrome' -or
        @($summary.privacy.targetPackages) -notcontains 'com.google.android.apps.messaging' -or
        $summary.privacy.attributionPackage -ne 'com.terasumi.sellerkeyboard' -or
        $summary.privacy.networkExpectation -ne 'Zero' -or
        -not $summary.privacy.networkAccountingSupported -or
        [long]$summary.privacy.rxDelta -ne 0 -or
        [long]$summary.privacy.txDelta -ne 0 -or
        @($summary.privacy.canaries).Count -ne 3 -or
        [int]$summary.privacy.canaryOccurrencesInUidLog -ne 0 -or
        -not (Test-Path -LiteralPath $privacySummary) -or
        -not (Test-Path -LiteralPath $privacyUidLog) -or
        -not (Test-Path -LiteralPath $privacyNetstats) -or
        [int]$summary.sellerFatalLogCount -ne 0) {
        Add-Result -Name 'API 36 named-app editors' -Status 'FAIL' `
            -Detail $summaryFile.FullName
        throw 'Named-app editor evidence is incomplete or exceeds its claim boundary.'
    }
    Add-Result -Name 'API 36 named-app editors' -Status 'PASS' -Detail (
        "Chrome URL/search plus Google Messages search and unsent SMS compose; " +
        "account-free, fictional recipient, unsubmitted; exact IME-UID network/log " +
        "attribution 0/0 bytes and zero canaries; $($summaryFile.FullName)"
    )
}

function Assert-AccountGatedTargetBoundaryEvidence {
    $evidenceRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-named-app-account-gates'
    $manifestFile = Get-ChildItem -Path $evidenceRoot -Filter 'manifest.json' -Recurse `
        -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $manifestFile) {
        throw "Named-app account-gate evidence is missing under $evidenceRoot"
    }
    $manifest = Get-Content -Raw -LiteralPath $manifestFile.FullName | ConvertFrom-Json
    $targets = @($manifest.targets)
    $artifacts = @($manifest.artifacts)
    $expectedPackages = @('com.google.android.gm', 'com.google.android.apps.docs')
    $violations = @(
        if ([int]$manifest.schemaVersion -ne 1) { 'schemaVersion' }
        if ($manifest.overall -ne 'BOUNDARY_CONFIRMED') { 'overall' }
        if ([int]$manifest.api -ne 36) { 'api' }
        if ([int]$manifest.accountRecordCount -ne 0) { 'accountRecordCount' }
        if ($manifest.defaultIme -ne
            'com.terasumi.sellerkeyboard/.service.SellerKeyboard') { 'defaultIme' }
        if ($manifest.autofillService -ne
            'com.google.android.gms/.autofill.service.AutofillService') { 'autofillService' }
        if ($manifest.credentialsEntered) { 'credentialsEntered' }
        if ($manifest.contentSubmitted) { 'contentSubmitted' }
        if ($targets.Count -ne 2) { 'targetCount' }
        foreach ($expectedPackage in $expectedPackages) {
            $target = @($targets | Where-Object package -eq $expectedPackage)
            if ($target.Count -ne 1) {
                "target:$expectedPackage"
            } elseif ($target[0].accountFreeEditorAvailable -or
                [string]::IsNullOrWhiteSpace([string]$target[0].versionName) -or
                [string]::IsNullOrWhiteSpace([string]$target[0].boundary)) {
                "targetBoundary:$expectedPackage"
            }
        }
        if ($artifacts.Count -ne 4) { 'artifactCount' }
        foreach ($artifact in $artifacts) {
            $artifactPath = Join-Path $manifestFile.DirectoryName $artifact.file
            if (-not (Test-Path -LiteralPath $artifactPath)) {
                "missingArtifact:$($artifact.file)"
                continue
            }
            $file = Get-Item -LiteralPath $artifactPath
            if ([long]$file.Length -ne [long]$artifact.bytes) {
                "artifactSize:$($artifact.file)"
            }
            $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $artifactPath).Hash
            if ($hash -ne [string]$artifact.sha256) {
                "artifactHash:$($artifact.file)"
            }
        }
    )
    if ($violations.Count -gt 0) {
        Add-Result -Name 'API 36 account-gated target boundary' -Status 'FAIL' `
            -Detail "$($violations -join ', '); $($manifestFile.FullName)"
        throw "Named-app account-gate evidence violations: $($violations -join ', ')"
    }
    Add-Result -Name 'API 36 account-gated target boundary' -Status 'PASS_SCOPED' -Detail (
        "Zero accounts; Gmail and Docs expose no account-free editor; Google Autofill is " +
        "configured but has no approved credential fixture; no credentials/content submitted; " +
        $manifestFile.FullName
    )
}

function Assert-InlineAutofillPlatformEvidence {
    $evidenceRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-inline-autofill'
    $summaryFile = Get-ChildItem -Path $evidenceRoot -Filter 'summary.json' -Recurse `
        -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $summaryFile) {
        throw "Platform inline-autofill evidence is missing under $evidenceRoot"
    }
    $summary = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
    $expectedUsernameHash =
        'f1a3067d8e9f339a197d04d0dc692b2d637edf90789fa07cbfdfa611645f7739'
    $expectedPasswordHash =
        '8a3b6a35c545d61c6fd15844c5429f195e0ae2a738ad44162ede0fe54baae6a6'
    $privacyRoles = @('ime', 'provider', 'host')
    $artifacts = @($summary.artifacts)
    $violations = @(
        if ([int]$summary.schemaVersion -ne 1) { 'schemaVersion' }
        if ($summary.overall -ne 'PASS') { 'overall' }
        if ([int]$summary.api -ne 36) { 'api' }
        if ($summary.imePackage -ne 'com.terasumi.sellerkeyboard') { 'imePackage' }
        if ($summary.hostPackage -ne
            'com.terasumi.sellerkeyboard.benchmarkhost') { 'hostPackage' }
        if ($summary.providerPackage -ne
            'com.terasumi.sellerkeyboard.autofilltest') { 'providerPackage' }
        if ([int]$summary.providerRequestCount -lt 1) { 'providerRequestCount' }
        if (-not $summary.inlineRequestPresent) { 'inlineRequestPresent' }
        if ([int]$summary.maxSuggestionCount -lt 1) { 'maxSuggestionCount' }
        if (-not $summary.datasetReturned) { 'datasetReturned' }
        if ($summary.imeCandidateSourceBefore -ne 'INLINE_AUTOFILL') {
            'imeCandidateSourceBefore'
        }
        if (-not $summary.imeSensitiveBefore) { 'imeSensitiveBefore' }
        if ([int]$summary.imeInlineCountBefore -ne 1) { 'imeInlineCountBefore' }
        if ([int]$summary.imeInlineCountAfter -ne 0) { 'imeInlineCountAfter' }
        if ([int]$summary.usernameLength -ne 31 -or
            $summary.usernameSha256 -ne $expectedUsernameHash) { 'usernameResult' }
        if ([int]$summary.passwordLength -ne 26 -or
            $summary.passwordSha256 -ne $expectedPasswordHash) { 'passwordResult' }
        foreach ($role in $privacyRoles) {
            $attribution = $summary.privacy.$role
            if (-not $attribution.networkAccountingSupported -or
                [long]$attribution.rxDelta -ne 0 -or [long]$attribution.txDelta -ne 0 -or
                [int]$attribution.canaryOccurrencesInUidLog -ne 0) {
                "privacy:$role"
            }
        }
        foreach ($property in @(
            'autofillServiceExact', 'defaultImeExact', 'showImeWithHardKeyboardExact',
            'providerPresenceExact', 'hostPresenceExact', 'imePresenceExact'
        )) {
            if (-not [bool]$summary.restoration.$property) { "restoration:$property" }
        }
        if ($artifacts.Count -ne 2) { 'artifactCount' }
        foreach ($artifact in $artifacts) {
            $artifactPath = Join-Path $summaryFile.DirectoryName $artifact.file
            if (-not (Test-Path -LiteralPath $artifactPath)) {
                "missingArtifact:$($artifact.file)"
                continue
            }
            $file = Get-Item -LiteralPath $artifactPath
            if ([long]$file.Length -ne [long]$artifact.bytes) {
                "artifactSize:$($artifact.file)"
            }
            if ((Get-FileHash -Algorithm SHA256 -LiteralPath $artifactPath).Hash -ne
                [string]$artifact.sha256) {
                "artifactHash:$($artifact.file)"
            }
        }
    )
    if ($violations.Count -gt 0) {
        Add-Result -Name 'API 36 platform inline autofill' -Status 'FAIL' -Detail (
            "$($violations -join ', '); $($summaryFile.FullName)"
        )
        throw "Platform inline-autofill evidence violations: $($violations -join ', ')"
    }
    Add-Result -Name 'API 36 platform inline autofill' -Status 'PASS' -Detail (
        "Distinct provider/host/IME UIDs; real inline request, visible dataset selection, exact " +
        "two-field fill, 0/0 bytes and zero controlled logs for all three UIDs, exact setting " +
        "and package-presence restoration; $($summaryFile.FullName)"
    )
}

function Assert-NamedPasswordManagerAutofillEvidence {
    $evidenceRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-named-password-manager'
    $summaryFile = Get-ChildItem -Path $evidenceRoot -Filter 'summary.json' -Recurse `
        -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $summaryFile) {
        throw "Named password-manager Autofill evidence is missing under $evidenceRoot"
    }
    $summary = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
    $flow = $summary.flow
    $expectedUsernameHash =
        'a54ec0a94200561a53e315a4ab6a1ea9bf927ccb88f6fcdf3f7880640c606c9c'
    $expectedPasswordHash =
        '13a6eb02a82bc1143a4204f91391d82030ee8811a0a758cb37b986ad6e2b3ce5'
    $expectedApkHash =
        'EFCCBC7722AEB93616AF541A5A5137418D7EAC42A36DD16391948BCFAADDCF79'
    $expectedCertificate =
        '7d55b8af210381aabf960f07e17cf7857b6d2a642ca2da6bf0bdf1b200362f04'
    $expectedPackages = [ordered]@{
        ime = 'com.terasumi.sellerkeyboard'
        provider = 'com.kunzisoft.keepass.libre'
        host = 'com.terasumi.sellerkeyboard.benchmarkhost'
    }
    $observedUids = @(
        [int]$flow.packages.imeUid,
        [int]$flow.packages.providerUid,
        [int]$flow.packages.hostUid
    )
    $artifacts = @($flow.artifacts)
    $artifactNames = @($artifacts | ForEach-Object { [string]$_.file })
    $violations = @(
        if ([int]$summary.schemaVersion -ne 1) { 'schemaVersion' }
        if ($summary.overall -ne 'PASS') { 'overall' }
        if ([int]$flow.api -ne 36) { 'api' }
        if ($flow.provider.name -ne 'KeePassDX' -or
            $flow.provider.package -ne $expectedPackages.provider -or
            $flow.provider.version -ne '4.4.5' -or
            [int]$flow.provider.versionCode -ne 44500) { 'providerIdentity' }
        if ($flow.provider.component -ne (
            'com.kunzisoft.keepass.libre/' +
            'com.kunzisoft.keepass.credentialprovider.autofill.KeeAutofillService'
        )) { 'providerComponent' }
        if ($flow.provider.officialReleaseUrl -ne (
            'https://github.com/Kunzisoft/KeePassDX/releases/download/4.4.5/' +
            'KeePassDX-4.4.5-libre.apk'
        )) { 'providerReleaseUrl' }
        if ($flow.provider.apkSha256 -ne $expectedApkHash) { 'providerApkHash' }
        if ($flow.provider.signingCertificateSha256 -ne $expectedCertificate) {
            'providerCertificate'
        }
        if (-not [bool]$flow.provider.localVault -or
            [bool]$flow.provider.cloudAccountUsed) { 'localVaultBoundary' }
        foreach ($role in $expectedPackages.Keys) {
            if ($flow.packages.$role -ne $expectedPackages[$role]) { "package:$role" }
        }
        if (-not [bool]$flow.packages.distinctUids -or
            @($observedUids | Sort-Object -Unique).Count -ne 3) { 'distinctUids' }
        if ($flow.initial.candidateSource -ne 'INLINE_AUTOFILL' -or
            [int]$flow.initial.inlineCount -ne 1) { 'initialInlineDataset' }
        if (-not [bool]$flow.entrySelection.providerOwnedPickerOpened -or
            [int]$flow.entrySelection.inlineCountAfterSelection -ne 2) {
            'providerOwnedPicker'
        }
        if ([int]$flow.fill.usernameLength -ne 30 -or
            $flow.fill.usernameSha256 -ne $expectedUsernameHash) { 'usernameResult' }
        if ([int]$flow.fill.passwordLength -ne 27 -or
            $flow.fill.passwordSha256 -ne $expectedPasswordHash) { 'passwordResult' }
        if ([int]$flow.fill.inlineCountAfterFill -ne 0) { 'inlineClearing' }
        foreach ($role in $expectedPackages.Keys) {
            $attribution = $flow.privacy.$role
            if ($attribution.packageName -ne $expectedPackages[$role] -or
                [int]$attribution.uid -ne [int]$flow.packages.("${role}Uid") -or
                -not [bool]$attribution.networkAccountingSupported -or
                [long]$attribution.rxDelta -ne 0 -or [long]$attribution.txDelta -ne 0 -or
                [int]$attribution.canaryOccurrencesInUidLog -ne 0) {
                "privacy:$role"
            }
        }
        foreach ($property in @(
            'autofillServiceExact', 'defaultImeExact', 'showImeWithHardKeyboardExact',
            'providerPresenceExact', 'hostPresenceExact', 'imePresenceExact',
            'fixtureVaultPresenceExact'
        )) {
            if (-not [bool]$summary.restoration.$property) { "restoration:$property" }
        }
        if ($artifacts.Count -lt 6) { 'artifactCount' }
        foreach ($requiredArtifact in @(
            'initial-sign-in.png', 'selected-entry-datasets.png', 'after-selection.png'
        )) {
            if ($requiredArtifact -notin $artifactNames) { "missingRequiredArtifact:$requiredArtifact" }
        }
        foreach ($artifact in $artifacts) {
            if ([System.IO.Path]::GetFileName([string]$artifact.file) -ne
                [string]$artifact.file) {
                "unsafeArtifactPath:$($artifact.file)"
                continue
            }
            $artifactPath = Join-Path $summaryFile.DirectoryName $artifact.file
            if (-not (Test-Path -LiteralPath $artifactPath)) {
                "missingArtifact:$($artifact.file)"
                continue
            }
            $file = Get-Item -LiteralPath $artifactPath
            if ([long]$file.Length -ne [long]$artifact.bytes -or
                [long]$file.Length -lt 1000) { "artifactSize:$($artifact.file)" }
            if ((Get-FileHash -Algorithm SHA256 -LiteralPath $artifactPath).Hash -ne
                [string]$artifact.sha256) { "artifactHash:$($artifact.file)" }
        }
    )
    if ($violations.Count -gt 0) {
        Add-Result -Name 'API 36 named password-manager inline autofill' -Status 'FAIL' `
            -Detail "$($violations -join ', '); $($summaryFile.FullName)"
        throw "Named password-manager Autofill evidence violations: $($violations -join ', ')"
    }
    Add-Result -Name 'API 36 named password-manager inline autofill' -Status 'PASS' -Detail (
        'KeePassDX 4.4.5 official signed APK; local fictional vault; provider-owned picker and ' +
        'visible inline credential selection; exact two-field fill; distinct IME/provider/host ' +
        'UIDs at 0/0 bytes with zero controlled logs; exact settings/package/vault restoration; ' +
        $summaryFile.FullName
    )
}

function Assert-EmulatorAccessibilityEvidence {
    $evidenceRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-accessibility'
    $summaryFile = Get-ChildItem -Path $evidenceRoot -Filter 'summary.json' -Recurse `
        -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $summaryFile) {
        throw "Emulator accessibility evidence is missing under $evidenceRoot"
    }
    $summary = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
    $switchFocusScreenshot = Join-Path $summaryFile.DirectoryName `
        $summary.artifacts.switchAccessKeyFocusedScreenshot
    $switchActivatedScreenshot = Join-Path $summaryFile.DirectoryName `
        $summary.artifacts.switchAccessKeyActivatedScreenshot
    $switchActivatedHierarchy = Join-Path $summaryFile.DirectoryName `
        $summary.artifacts.switchAccessKeyActivatedHierarchy
    $switchInputDevice = Join-Path $summaryFile.DirectoryName `
        $summary.artifacts.switchAccessInputDevice
    if ($summary.overall -ne 'PARTIAL_PASS' -or [int]$summary.api -ne 36 -or
        -not $summary.windowedEmulator -or
        -not $summary.talkBack.serviceEnabledAndBound -or
        -not $summary.talkBack.sellerImeVisible -or
        [int]$summary.talkBack.sellerClickableSemanticNodeCount -lt 26 -or
        -not $summary.talkBack.hostGeneratedSwipeTraversalVerified -or
        $summary.talkBack.focusedControlDescription -ne '1' -or
        -not $summary.talkBack.hostGeneratedDoubleTapActionVerified -or
        $summary.talkBack.exactEditorResult -ne '1' -or
        -not $summary.switchAccess.serviceEnabledAndBound -or
        -not $summary.switchAccess.sellerImeVisible -or
        [int]$summary.switchAccess.sellerClickableSemanticNodeCount -lt 26 -or
        $summary.switchAccess.setupMode -ne 'two-switch' -or
        $summary.switchAccess.scanningMethod -ne 'views_linear_ime_row_col_key' -or
        [int]$summary.switchAccess.nextAndroidKeyCode -ne 24 -or
        [int]$summary.switchAccess.selectAndroidKeyCode -ne 25 -or
        -not $summary.switchAccess.emulatedUsbSwitchReportedExternal -or
        -not $summary.switchAccess.rowScanningVerified -or
        -not $summary.switchAccess.keyFocusVerified -or
        $summary.switchAccess.focusedControlDescription -ne '1' -or
        -not $summary.switchAccess.scanningAndActionVerified -or
        $summary.switchAccess.exactEditorResult -ne '1' -or
        -not (Test-Path -LiteralPath $switchFocusScreenshot -PathType Leaf) -or
        -not (Test-Path -LiteralPath $switchActivatedScreenshot -PathType Leaf) -or
        -not (Test-Path -LiteralPath $switchActivatedHierarchy -PathType Leaf) -or
        -not (Test-Path -LiteralPath $switchInputDevice -PathType Leaf) -or
        -not $summary.avdKeyboardDetected -or $summary.avdKeyboardReportedExternal -or
        -not $summary.emulatedUsbSwitchDetected -or
        -not $summary.emulatedUsbSwitchReportedExternal -or
        -not $summary.talkBackTraversalAndActionVerified -or
        -not $summary.switchAccessScanningAndActionVerified -or
        $summary.physicalExternalKeyboardVerified) {
        Add-Result -Name 'API 36 accessibility coexistence' -Status 'FAIL' `
            -Detail $summaryFile.FullName
        throw 'Accessibility evidence is incomplete or exceeds its emulator claim boundary.'
    }
    Add-Result -Name 'API 36 accessibility coexistence' -Status 'PASS_SCOPED' -Detail (
        "TalkBack host-gesture and two-switch USB-uinput traversal/actions pass with exact editor results; " +
        "Switch Access exposes $($summary.switchAccess.sellerClickableSemanticNodeCount) semantic controls; " +
        "a real physical external keyboard remains release-blocking; $($summaryFile.FullName)"
    )
}

function Assert-EmulatorLargeWindowEvidence {
    $evidenceRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-large-window'
    $summaryFile = Get-ChildItem -Path $evidenceRoot -Filter 'summary.json' -Recurse `
        -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $summaryFile) {
        throw "Emulator large-window evidence is missing under $evidenceRoot"
    }
    $summary = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
    $artifactProperties = @(
        'nativeLandscapeScreenshot',
        'nativeLandscapeHierarchy',
        'nativePortraitScreenshot',
        'nativePortraitHierarchy',
        'foldableWidthScreenshot',
        'foldableWidthHierarchy',
        'foldableWidthTypedScreenshot',
        'foldableWidthTypedHierarchy',
        'restoredLandscapeTypedScreenshot',
        'restoredLandscapeTypedHierarchy',
        'deviceStateNotes'
    )
    $missingArtifacts = @(
        $artifactProperties | Where-Object {
            $artifact = $summary.artifacts.$_
            [string]::IsNullOrWhiteSpace($artifact) -or
                -not (Test-Path -LiteralPath (
                    Join-Path $summaryFile.DirectoryName $artifact
                ) -PathType Leaf)
        }
    )
    if ($summary.overall -ne 'PASS_SCOPED' -or [int]$summary.api -ne 36 -or
        $summary.avd -ne 'SellerKeyboardApi36Tablet' -or
        -not $summary.coldBootWithoutSnapshotLoadOrSave -or
        [int]$summary.densityDpi -ne 320 -or
        [int]$summary.nativeLandscape.logicalWidthPx -ne 2560 -or
        [int]$summary.nativeLandscape.logicalHeightPx -ne 1600 -or
        [int]$summary.nativeLandscape.clickableSemanticNodeCount -lt 35 -or
        [double]$summary.nativeLandscape.minimumLabeledControlHeightDp -lt 48 -or
        $summary.nativeLandscape.visualOverlapDetected -or
        [int]$summary.nativePortrait.logicalWidthPx -ne 1600 -or
        [int]$summary.nativePortrait.logicalHeightPx -ne 2560 -or
        [int]$summary.nativePortrait.clickableSemanticNodeCount -lt 45 -or
        [double]$summary.nativePortrait.minimumLabeledControlHeightDp -lt 48 -or
        $summary.nativePortrait.visualOverlapDetected -or
        -not $summary.nativePortrait.imeRemainedShownAfterRotation -or
        [int]$summary.foldableWidthSimulation.logicalWidthDp -ne 600 -or
        [int]$summary.foldableWidthSimulation.logicalHeightDp -ne 800 -or
        [int]$summary.foldableWidthSimulation.clickableSemanticNodeCount -lt 45 -or
        [double]$summary.foldableWidthSimulation.minimumLabeledControlHeightDp -lt 48 -or
        [double]$summary.foldableWidthSimulation.minimumPrimaryLetterKeyWidthDp -lt 54 -or
        $summary.foldableWidthSimulation.visualOverlapDetected -or
        -not $summary.foldableWidthSimulation.touchTypingVerified -or
        $summary.foldableWidthSimulation.exactEditorResult -ne 'skqvz' -or
        -not $summary.nativeSizeRestore.imeReattachedAndShown -or
        -not $summary.nativeSizeRestore.postRestoreTouchTypingVerified -or
        $summary.nativeSizeRestore.postRestoreExactEditorResult -ne 'skqvz' -or
        $summary.realFoldPostureVerified -or
        @($summary.supportedDeviceStates).Count -ne 1 -or
        @($summary.supportedDeviceStates)[0] -ne 'DEFAULT' -or
        [int]$summary.sellerFatalCrashCount -ne 0 -or
        $missingArtifacts.Count -ne 0) {
        Add-Result -Name 'API 36 tablet and foldable-width visuals' -Status 'FAIL' `
            -Detail $summaryFile.FullName
        throw 'Large-window evidence is incomplete or exceeds its emulator claim boundary.'
    }
    Add-Result -Name 'API 36 tablet and foldable-width visuals' -Status 'PASS_SCOPED' -Detail (
        'Native tablet portrait/landscape and 600 dp live-resized window keep 48 dp controls, ' +
        'complete semantics, exact touch typing, and post-resize IME recovery; this tablet AVD ' +
        "has no hinge sensor, while Pixel Fold posture is checked separately; $($summaryFile.FullName)"
    )
}

function Assert-EmulatorFoldablePostureEvidence {
    $evidenceRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-foldable-posture'
    $summaryFile = Get-ChildItem -Path $evidenceRoot -Filter 'summary.json' -Recurse `
        -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $summaryFile) {
        throw "Emulator foldable-posture evidence is missing under $evidenceRoot"
    }
    $summary = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
    $artifactNames = @(
        $summary.artifacts.avdConfig,
        $summary.artifacts.supportedStates,
        $summary.artifacts.finalDeviceStateDump,
        $summary.artifacts.fatalLogMatches,
        $summary.opened.screenshot,
        $summary.opened.hierarchy,
        $summary.halfOpened.screenshot,
        $summary.halfOpened.hierarchy,
        $summary.closedOuterDisplay.screenshot,
        $summary.closedOuterDisplay.hierarchy,
        $summary.reopened.screenshot,
        $summary.reopened.hierarchy
    )
    $missingArtifacts = @(
        $artifactNames | Where-Object {
            [string]::IsNullOrWhiteSpace($_) -or
                -not (Test-Path -LiteralPath (
                    Join-Path $summaryFile.DirectoryName $_
                ) -PathType Leaf)
        }
    )
    if ($summary.overall -ne 'PASS_SCOPED' -or [int]$summary.api -ne 36 -or
        $summary.avd -ne 'SellerKeyboardApi36Fold' -or
        $summary.avdHardwareProfile -ne 'pixel_fold' -or
        -not $summary.coldBootWithoutSnapshotLoadOrSave -or
        -not $summary.hingeSensorDeclared -or
        @($summary.supportedDeviceStates).Count -ne 4 -or
        @($summary.supportedDeviceStates) -notcontains 'CLOSED' -or
        @($summary.supportedDeviceStates) -notcontains 'HALF_OPENED' -or
        @($summary.supportedDeviceStates) -notcontains 'OPENED' -or
        @($summary.supportedDeviceStates) -notcontains 'REAR_DISPLAY_MODE' -or
        [int]$summary.densityDpi -ne 420 -or
        [int]$summary.opened.hingeAngle -ne 180 -or
        [int]$summary.opened.deviceState -ne 2 -or
        [int]$summary.opened.physicalWidthPx -ne 2208 -or
        [int]$summary.opened.physicalHeightPx -ne 1840 -or
        $summary.opened.hostRelaunchRequired -or
        -not $summary.opened.imeSession.inputShown -or
        $summary.opened.exactEditorText -ne 'qvbn' -or
        -not $summary.opened.exactEditorTextVerified -or
        [int]$summary.opened.clickableSemanticNodeCount -lt 45 -or
        [double]$summary.opened.minimumLabeledControlHeightDp -lt 48 -or
        [int]$summary.halfOpened.hingeAngle -ne 90 -or
        [int]$summary.halfOpened.deviceState -ne 1 -or
        $summary.halfOpened.hostRelaunchRequired -or
        -not $summary.halfOpened.imeSession.inputShown -or
        $summary.halfOpened.exactEditorText -ne 'klmn' -or
        -not $summary.halfOpened.exactEditorTextVerified -or
        [int]$summary.halfOpened.clickableSemanticNodeCount -lt 45 -or
        [double]$summary.halfOpened.minimumLabeledControlHeightDp -lt 48 -or
        [int]$summary.closedOuterDisplay.hingeAngle -ne 0 -or
        [int]$summary.closedOuterDisplay.deviceState -ne 0 -or
        [int]$summary.closedOuterDisplay.physicalWidthPx -ne 1080 -or
        [int]$summary.closedOuterDisplay.physicalHeightPx -ne 2092 -or
        $summary.closedOuterDisplay.hostRelaunchRequired -or
        -not $summary.closedOuterDisplay.imeSession.inputShown -or
        $summary.closedOuterDisplay.exactEditorText -ne 'bnmp' -or
        -not $summary.closedOuterDisplay.exactEditorTextVerified -or
        [int]$summary.closedOuterDisplay.clickableSemanticNodeCount -lt 50 -or
        [double]$summary.closedOuterDisplay.minimumLabeledControlHeightDp -lt 48 -or
        [int]$summary.reopened.hingeAngle -ne 180 -or
        [int]$summary.reopened.deviceState -ne 2 -or
        $summary.reopened.hostRelaunchRequired -or
        -not $summary.reopened.imeSession.inputShown -or
        $summary.reopened.exactEditorText -ne 'ptgv' -or
        -not $summary.reopened.exactEditorTextVerified -or
        [int]$summary.reopened.clickableSemanticNodeCount -lt 45 -or
        [double]$summary.reopened.minimumLabeledControlHeightDp -lt 48 -or
        [int]$summary.sellerOrHostFatalLogCount -ne 0 -or
        $summary.realPhysicalFoldPostureVerified -or
        $missingArtifacts.Count -ne 0) {
        Add-Result -Name 'API 36 Pixel Fold posture transitions' -Status 'FAIL' `
            -Detail $summaryFile.FullName
        throw 'Foldable-posture evidence is incomplete or exceeds its emulator claim boundary.'
    }
    Add-Result -Name 'API 36 Pixel Fold posture transitions' -Status 'PASS_SCOPED' `
        -Detail (
            'Declared hinge sensor and Android OPENED/HALF_OPENED/CLOSED states preserve ' +
            '48 dp semantics and exact visible-key typing across inner, outer, and reopened ' +
            "displays without host relaunch; physical foldable acceptance remains open; " +
            $summaryFile.FullName
        )
}

function Assert-EmulatorLowMemoryEvidence {
    $evidenceRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-low-memory'
    $summaryFile = Get-ChildItem -Path $evidenceRoot -Filter 'summary.json' -Recurse `
        -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $summaryFile) {
        throw "Emulator low-memory evidence is missing under $evidenceRoot"
    }
    $summary = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
    $artifactProperties = @(
        'beforeScreenshot',
        'beforeHierarchy',
        'recreatedEmptyScreenshot',
        'recreatedEmptyHierarchy',
        'recreatedTypedScreenshot',
        'recreatedTypedHierarchy',
        'processExitInfo',
        'fatalLogMatches'
    )
    $missingArtifacts = @(
        $artifactProperties | Where-Object {
            $artifact = $summary.artifacts.$_
            [string]::IsNullOrWhiteSpace($artifact) -or
                -not (Test-Path -LiteralPath (
                    Join-Path $summaryFile.DirectoryName $artifact
                ) -PathType Leaf)
        }
    )
    $exitInfoPath = Join-Path $summaryFile.DirectoryName `
        $summary.artifacts.processExitInfo
    $exitInfo = if (Test-Path -LiteralPath $exitInfoPath -PathType Leaf) {
        Get-Content -Raw -LiteralPath $exitInfoPath
    } else { '' }
    $pidBefore = [int]$summary.simulation.pidBefore
    $pidAfter = [int]$summary.simulation.pidAfter
    $killExitPattern = '(?s)pid=' + $pidBefore +
        '.*reason=10 \(USER REQUESTED\).*subreason=24 \(KILL BACKGROUND\)'
    if ($summary.overall -ne 'PASS' -or [int]$summary.api -ne 36 -or
        $summary.hostPackage -ne 'com.terasumi.sellerkeyboard.benchmarkhost' -or
        $summary.simulation.trimMemoryLevel -ne 'COMPLETE' -or
        $summary.simulation.backgroundKillCommand -ne (
            'am kill --user current com.terasumi.sellerkeyboard.benchmarkhost'
        ) -or
        -not $summary.simulation.processActuallyDied -or
        -not $summary.simulation.pidChanged -or
        $pidBefore -le 0 -or $pidAfter -le 0 -or $pidBefore -eq $pidAfter -or
        $summary.beforeProcessDeath.exactEditorText -ne 'skqvz' -or
        -not $summary.beforeProcessDeath.exactEditorTextVerified -or
        -not $summary.beforeProcessDeath.imeSession.inputShown -or
        $summary.beforeProcessDeath.imeSession.targetPackage -ne
            'com.terasumi.sellerkeyboard.benchmarkhost' -or
        -not $summary.afterProcessRecreation.emptyEditorVerified -or
        -not $summary.afterProcessRecreation.staleTextAbsent -or
        $summary.afterProcessRecreation.exactRecoveryText -ne 'mnbvc' -or
        -not $summary.afterProcessRecreation.exactRecoveryTextVerified -or
        -not $summary.afterProcessRecreation.imeReattachedAndShown -or
        -not $summary.afterProcessRecreation.imeSession.inputShown -or
        $summary.afterProcessRecreation.imeSession.targetPackage -ne
            'com.terasumi.sellerkeyboard.benchmarkhost' -or
        [int]$summary.sellerOrHostFatalLogCount -ne 0 -or
        $exitInfo -notmatch $killExitPattern -or
        $exitInfo -notmatch 'description=kill background' -or
        $missingArtifacts.Count -ne 0) {
        Add-Result -Name 'API 36 low-memory process recreation' -Status 'FAIL' `
            -Detail $summaryFile.FullName
        throw 'Low-memory evidence is incomplete or exceeds its emulator claim boundary.'
    }
    Add-Result -Name 'API 36 low-memory process recreation' -Status 'PASS_SCOPED' `
        -Detail (
            "TRIM_MEMORY_COMPLETE plus Android background-process kill changed PID " +
            "$pidBefore->$pidAfter; the IME reattached, rejected stale text, and entered " +
            "exact recovery text through visible keys; $($summaryFile.FullName)"
        )
}

function Assert-EmulatorApiMatrixEvidence {
    $matrixRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-api-matrix\latest'
    $matrixSummaryPath = Join-Path $matrixRoot 'summary.json'
    $expectedApis = @(26, 30, 31, 34, 35)
    if (-not (Test-Path -LiteralPath $matrixSummaryPath)) {
        Add-Result -Name 'Emulator API compatibility matrix' -Status 'FAIL' `
            -Detail "Missing $matrixSummaryPath"
        throw "Missing emulator API matrix evidence: $matrixSummaryPath"
    }

    $violations = [System.Collections.Generic.List[string]]::new()
    try {
        $matrix = Get-Content -Raw -LiteralPath $matrixSummaryPath | ConvertFrom-Json
        if ($matrix.overall -ne 'PASS') { $violations.Add('matrixOverall') }
        if ([int]$matrix.passCount -ne $expectedApis.Count) { $violations.Add('passCount') }
        if ([int]$matrix.failCount -ne 0) { $violations.Add('failCount') }
        $actualApis = @($matrix.apiLevels | ForEach-Object { [int]$_ } | Sort-Object)
        if (($actualApis -join ',') -ne (($expectedApis | Sort-Object) -join ',')) {
            $violations.Add('apiLevels')
        }
        $runs = @($matrix.runs)
        if ($runs.Count -ne $expectedApis.Count) { $violations.Add('runCount') }

        $testApk = Get-Item -LiteralPath (Join-Path $repositoryRoot `
            'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk') `
            -ErrorAction SilentlyContinue
        if (-not $testApk) { $violations.Add('testApkMissing') }
        foreach ($api in $expectedApis) {
            $matchingRuns = @($runs | Where-Object { [int]$_.api -eq $api })
            if ($matchingRuns.Count -ne 1) {
                $violations.Add("api${api}Run")
                continue
            }
            $run = $matchingRuns[0]
            if ($run.overall -ne 'PASS' -or [int]$run.results -ne 22 -or
                [int]$run.optionalBackendSkips -ne 2) {
                $violations.Add("api${api}Aggregate")
                continue
            }
            $evidencePath = [System.IO.Path]::GetFullPath((Join-Path $matrixRoot `
                ([string]$run.evidence)))
            if (-not (Test-Path -LiteralPath $evidencePath)) {
                $violations.Add("api${api}EvidenceMissing")
                continue
            }
            $evidenceFile = Get-Item -LiteralPath $evidencePath
            $summary = Get-Content -Raw -LiteralPath $evidencePath | ConvertFrom-Json
            if ($summary.overall -ne 'PASS' -or [int]$summary.api -ne $api -or
                [int]$summary.results -ne 22 -or
                [int]$summary.optionalBackendSkips -ne 2 -or
                -not [bool]$summary.sensitiveTransitionVerified -or
                -not [bool]$summary.clipboardLifecycleVerified -or
                [int]$summary.richContentTargetCases -ne 3 -or
                -not [bool]$summary.crossAppProfilesVerified -or
                @($summary.privacyCases).Count -ne 3 -or
                [int]$summary.privacyCanaryOccurrencesInUidLog -ne 0 -or
                -not [bool]$summary.recordAudioPermissionRestoredExactly) {
                $violations.Add("api${api}Evidence")
            }
            if ($testApk -and $evidenceFile.LastWriteTimeUtc -lt $testApk.LastWriteTimeUtc) {
                $violations.Add("api${api}Stale")
            }
        }

        $api36EvidencePath = Join-Path $repositoryRoot `
            'docs\evidence\verification\emulator-connected\SellerKeyboardApi36Privacy\latest\summary.json'
        if (-not (Test-Path -LiteralPath $api36EvidencePath)) {
            $violations.Add('api36EvidenceMissing')
        } else {
            $api36File = Get-Item -LiteralPath $api36EvidencePath
            $api36 = Get-Content -Raw -LiteralPath $api36EvidencePath | ConvertFrom-Json
            if ($api36.overall -ne 'PASS' -or [int]$api36.api -ne 36 -or
                [int]$api36.results -ne 22 -or
                [int]$api36.optionalBackendSkips -ne 2 -or
                -not [bool]$api36.sensitiveTransitionVerified -or
                -not [bool]$api36.clipboardLifecycleVerified -or
                [int]$api36.richContentTargetCases -ne 3 -or
                -not [bool]$api36.crossAppProfilesVerified -or
                -not [bool]$api36.inlineAutofillPlatformVerified -or
                -not [bool]$api36.sellerWorkflowVerified -or
                -not [bool]$api36.coreInteractionsVerified -or
                @($api36.privacyCases).Count -ne 3 -or
                [int]$api36.privacyCanaryOccurrencesInUidLog -ne 0 -or
                -not [bool]$api36.recordAudioPermissionRestoredExactly) {
                $violations.Add('api36Evidence')
            }
            if ($testApk -and $api36File.LastWriteTimeUtc -lt $testApk.LastWriteTimeUtc) {
                $violations.Add('api36Stale')
            }
        }
    } catch {
        $violations.Add("parse:$($_.Exception.Message)")
    }

    if ($violations.Count -gt 0) {
        Add-Result -Name 'Emulator API compatibility matrix' -Status 'FAIL' `
            -Detail "$($violations -join ', '); $matrixSummaryPath"
        throw "Emulator API matrix evidence violations: $($violations -join ', ')"
    }
    Add-Result -Name 'Emulator API compatibility matrix' -Status 'PASS_EMULATOR' `
        -Detail (
            'API 26/30/31/34/35/36 each pass 22 connected results plus sensitive, ' +
            'clipboard, rich-content, cross-app, privacy, and exact permission restoration; ' +
            'this does not replace physical-device/OEM evidence; ' + $matrixSummaryPath
        )
}

function Assert-ProductImageSourcePolicyEvidence {
    $evidenceRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-rich-content'
    $summaryFiles = @(
        Get-ChildItem -Path $evidenceRoot -Filter 'summary.json' -Recurse `
            -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending
    )
    $selected = $null
    foreach ($summaryFile in $summaryFiles) {
        $candidate = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
        if ($candidate.overall -eq 'PASS' -and
            $null -ne $candidate.productionPolicyRequests) {
            $selected = [pscustomobject]@{ File = $summaryFile; Data = $candidate }
            break
        }
    }
    if ($null -eq $selected) {
        Add-Result -Name 'Production product-image source policy' -Status 'FAIL' `
            -Detail $evidenceRoot
        throw 'Production product-image source-policy evidence is missing.'
    }

    $summary = $selected.Data
    $flow = $summary.flowSummary
    $cases = @($flow.cases)
    $accept = $cases | Where-Object mode -eq 'accept' | Select-Object -First 1
    $reject = $cases | Where-Object mode -eq 'reject' | Select-Object -First 1
    $unsupported = $cases | Where-Object mode -eq 'unsupported' | Select-Object -First 1
    $production = $flow.productionSourcePolicy
    $violations = @(
        if ([int]$summary.acceptRequests -ne 1) { 'acceptRequests' }
        if ([int]$summary.rejectRequests -ne 1) { 'rejectRequests' }
        if ([int]$summary.unsupportedRequests -ne 0) { 'unsupportedRequests' }
        if ([int]$summary.productionPolicyRequests -ne 0) { 'productionPolicyRequests' }
        if ([int]$summary.controlledTextOccurrencesInUidLog -ne 0) { 'controlledTextLog' }
        if ($cases.Count -ne 3) { 'caseCount' }
        if ($accept.expectedEditorText -ne 'RICH_ACCEPTED:image/jpeg') { 'acceptOutcome' }
        if ($reject.expectedEditorText -ne `
            'Seller product | VND 100000 | https://example.invalid/product') {
            'rejectFallback'
        }
        if ($unsupported.expectedEditorText -ne `
            'Seller product | VND 100000 | https://example.invalid/product') {
            'unsupportedFallback'
        }
        if ($production.expected -ne 'text fallback without image fetch') {
            'productionFallback'
        }
        if (-not $production.editorAdvertisedJpeg) { 'productionJpegCapability' }
        if ($production.source -notmatch '^http://127\.0\.0\.1:18080/') {
            'productionCleartextFixture'
        }
        foreach ($path in @(
            $accept.resultWindow,
            $reject.resultWindow,
            $unsupported.resultWindow,
            $production.resultWindow
        )) {
            if ([string]::IsNullOrWhiteSpace([string]$path) -or
                -not (Test-Path -LiteralPath $path)) {
                'missingResultWindow'
            }
        }
    )
    if ($violations.Count -gt 0) {
        Add-Result -Name 'Production product-image source policy' -Status 'FAIL' `
            -Detail "$($violations -join ', '); $($selected.File.FullName)"
        throw "Product-image source-policy evidence violations: $($violations -join ', ')"
    }
    Add-Result -Name 'Production product-image source policy' -Status 'PASS' -Detail (
        'authorized debug fixture accept/reject requests 1/1; unsupported and ' +
        "production cleartext requests 0/0; $($selected.File.FullName)"
    )
}

function Assert-PersonalDictionaryEvidence {
    $evidenceRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-dictionary'
    $summaryFiles = @(
        Get-ChildItem -Path $evidenceRoot -Filter 'summary.json' -Recurse `
            -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending
    )
    $selected = $null
    foreach ($summaryFile in $summaryFiles) {
        $candidate = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
        if ($candidate.overall -eq 'PASS' -and
            $null -ne $candidate.realCandidatePipelineVerified -and
            $null -ne $candidate.originalDictionaryRestoredExactly) {
            $selected = [pscustomobject]@{ File = $summaryFile; Data = $candidate }
            break
        }
    }
    if ($null -eq $selected) {
        Add-Result -Name 'API 36 personal dictionary flow' -Status 'FAIL' `
            -Detail $evidenceRoot
        throw 'Personal-dictionary emulator evidence is missing.'
    }

    $summary = $selected.Data
    $violations = @(
        if ([int]$summary.api -ne 36) { 'api' }
        foreach ($property in @(
            'productUiAddVerified',
            'persistenceAfterProcessRecreationVerified',
            'realCandidatePipelineVerified',
            'candidateCommittedExact',
            'sensitiveFieldSuppressionVerified',
            'productUiRemoveVerified',
            'removePersistenceVerified',
            'productUiClearVerified',
            'clearPersistenceVerified',
            'learningEnabledDuringFixture',
            'originalDictionaryRestoredExactly',
            'originalSettingsRestoredExactly'
        )) {
            if (-not [bool]$summary.$property) { $property }
        }
        if ($summary.deterministicLanguageMode -ne 'ENGLISH') { 'languageMode' }
        if (-not $summary.networkAccountingSupported) { 'networkAccountingUnsupported' }
        if ([long]$summary.uidRxDelta -ne 0) { 'uidRxDelta' }
        if ([long]$summary.uidTxDelta -ne 0) { 'uidTxDelta' }
        if ([int]$summary.controlledWordOccurrencesInUidLog -ne 0) {
            'controlledWordLog'
        }
        foreach ($path in @(
            $summary.artifacts.addedUi,
            $summary.artifacts.persistedUi,
            $summary.artifacts.candidateUi,
            $summary.artifacts.sensitiveUi,
            $summary.artifacts.removedUi,
            $summary.artifacts.removePersistedUi,
            $summary.artifacts.clearedUi,
            $summary.artifacts.clearPersistedUi,
            $summary.artifacts.attributionSummary
        )) {
            $artifactPath = [string]$path
            if (-not [System.IO.Path]::IsPathRooted($artifactPath)) {
                $artifactPath = Join-Path $repositoryRoot $artifactPath
            }
            if ([string]::IsNullOrWhiteSpace([string]$path) -or
                -not (Test-Path -LiteralPath $artifactPath)) {
                'missingArtifact'
            }
        }
    )
    if ($violations.Count -gt 0) {
        Add-Result -Name 'API 36 personal dictionary flow' -Status 'FAIL' `
            -Detail "$($violations -join ', '); $($selected.File.FullName)"
        throw "Personal-dictionary evidence violations: $($violations -join ', ')"
    }
    Add-Result -Name 'API 36 personal dictionary flow' -Status 'PASS_SCOPED' `
        -Detail (
            'real UI add/remove/clear and process persistence pass; exact personal candidate ' +
            'commit and sensitive suppression pass; UID RX/TX 0/0 and controlled-word logs 0; ' +
            $selected.File.FullName
        )
}

function Assert-EmojiEvidence {
    $evidenceRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-emoji'
    $summaryFiles = @(
        Get-ChildItem -Path $evidenceRoot -Filter 'summary.json' -Recurse `
            -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending
    )
    $selected = $null
    foreach ($summaryFile in $summaryFiles) {
        $candidate = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
        if ($candidate.overall -eq 'PASS' -and
            $null -ne $candidate.touchOnlySearchVerified -and
            $null -ne $candidate.originalVariantSelectionRestoredExactly) {
            $selected = [pscustomobject]@{ File = $summaryFile; Data = $candidate }
            break
        }
    }
    if ($null -eq $selected) {
        Add-Result -Name 'API 36 emoji flow' -Status 'FAIL' -Detail $evidenceRoot
        throw 'Emoji emulator evidence is missing.'
    }

    $summary = $selected.Data
    $violations = @(
        if ([int]$summary.api -ne 36) { 'api' }
        foreach ($property in @(
            'realInstalledImeOpenVerified',
            'touchOnlySearchVerified',
            'searchCommitExact',
            'categoryNavigationVerified',
            'longPressVariantPopupVerified',
            'variantCommitExact',
            'recentsPersistenceAfterProcessRecreationVerified',
            'productUiClearVerified',
            'clearPersistenceAfterProcessRecreationVerified',
            'sensitiveFieldSuppressionVerified',
            'originalRecentsRestoredExactly',
            'originalVariantSelectionRestoredExactly'
        )) {
            if (-not [bool]$summary.$property) { $property }
        }
        if ([int]$summary.semanticAlphabeticKeyCount -lt 29) { 'semanticKeyCount' }
        foreach ($measurement in @(
            'searchFieldHeightDp',
            'searchKeyHeightDp',
            'searchResultHeightDp',
            'clearActionHeightDp'
        )) {
            if ([double]$summary.$measurement -lt 48.0) { $measurement }
        }
        if (-not $summary.networkAccountingSupported) { 'networkAccountingUnsupported' }
        if ([long]$summary.uidRxDelta -ne 0) { 'uidRxDelta' }
        if ([long]$summary.uidTxDelta -ne 0) { 'uidTxDelta' }
        if ([int]$summary.controlledValueOccurrencesInUidLog -ne 0) {
            'controlledValueLog'
        }
        foreach ($path in @(
            $summary.artifacts.openUi,
            $summary.artifacts.touchSearchKeypadUi,
            $summary.artifacts.searchResultUi,
            $summary.artifacts.persistedRecentUi,
            $summary.artifacts.variantPopupUi,
            $summary.artifacts.clearedUi,
            $summary.artifacts.clearPersistedUi,
            $summary.artifacts.sensitiveUi,
            $summary.artifacts.attributionSummary
        )) {
            $artifactPath = [string]$path
            if (-not [System.IO.Path]::IsPathRooted($artifactPath)) {
                $artifactPath = Join-Path $repositoryRoot $artifactPath
            }
            if ([string]::IsNullOrWhiteSpace([string]$path) -or
                -not (Test-Path -LiteralPath $artifactPath)) {
                'missingArtifact'
            }
        }
    )
    if ($violations.Count -gt 0) {
        Add-Result -Name 'API 36 emoji flow' -Status 'FAIL' `
            -Detail "$($violations -join ', '); $($selected.File.FullName)"
        throw "Emoji evidence violations: $($violations -join ', ')"
    }
    Add-Result -Name 'API 36 emoji flow' -Status 'PASS_SCOPED' `
        -Detail (
            'real touch-only search/category/variant commits pass; recents and Clear persist ' +
            'across process recreation; sensitive suppression passes; 48 dp semantics pass; ' +
            'UID RX/TX 0/0 and controlled-value logs 0; exact recents/variant rollback; ' +
            $selected.File.FullName
        )
}

function Assert-DeterministicVoiceRecognitionEvidence {
    $evidenceRoot = Join-Path $repositoryRoot 'docs\evidence\verification\emulator-voice'
    $summaryFiles = @(
        Get-ChildItem -Path $evidenceRoot -Filter 'summary.json' -Recurse `
            -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending
    )
    $selected = $null
    foreach ($summaryFile in $summaryFiles) {
        $candidate = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
        if ($candidate.overall -eq 'PASS' -and
            $candidate.provider -eq `
                'debug-only explicit RecognitionService through Android SpeechRecognizer binder' -and
            $null -ne $candidate.appUidRxDelta -and
            $null -ne $candidate.providerUidRxDelta) {
            $selected = [pscustomobject]@{ File = $summaryFile; Data = $candidate }
            break
        }
    }
    if ($null -eq $selected) {
        Add-Result -Name 'Deterministic voice-recognition integration' -Status 'FAIL' `
            -Detail $evidenceRoot
        throw 'Dual-UID deterministic voice-recognition evidence is missing.'
    }

    $summary = $selected.Data
    $cases = @($summary.cases)
    $lifecycle = @($summary.lifecycle)
    $mappedErrors = @($summary.mappedErrors)
    $requiredCases = @(
        'a_explicitService_deliversPartialProcessingAndReviewableFinalResult',
        'b_explicitService_mapsNetworkAndNoMatchErrors',
        'z_cancel_preventsDelayedProviderResultFromRepopulatingState'
    )
    $violations = @(
        if (-not $summary.productionProviderUntouched) { 'productionProviderTouched' }
        if ([int]$summary.testCount -ne 3) { 'testCount' }
        foreach ($case in $requiredCases) {
            if ($cases -notcontains $case) { "missingCase:$case" }
        }
        foreach ($state in @('Listening(partial)', 'Processing', 'Result(review required)')) {
            if ($lifecycle -notcontains $state) { "missingLifecycle:$state" }
        }
        foreach ($error in @('Network', 'NoMatch')) {
            if ($mappedErrors -notcontains $error) { "missingError:$error" }
        }
        if ($summary.lateResultAfterCancel) { 'lateResultAfterCancel' }
        if (-not $summary.networkAccountingSupported) { 'networkAccountingUnsupported' }
        if ([long]$summary.appUidRxDelta -ne 0) { 'appUidRxDelta' }
        if ([long]$summary.appUidTxDelta -ne 0) { 'appUidTxDelta' }
        if ([long]$summary.providerUidRxDelta -ne 0) { 'providerUidRxDelta' }
        if ([long]$summary.providerUidTxDelta -ne 0) { 'providerUidTxDelta' }
        if ([int]$summary.controlledTranscriptOccurrencesInUidLog -ne 0) {
            'controlledTranscriptLog'
        }
        foreach ($path in @(
            $summary.instrumentationLog,
            $summary.appAttributionSummary,
            $summary.providerAttributionSummary
        )) {
            if ([string]::IsNullOrWhiteSpace([string]$path) -or
                -not (Test-Path -LiteralPath $path)) {
                'missingArtifact'
            }
        }
    )
    if ($violations.Count -gt 0) {
        Add-Result -Name 'Deterministic voice-recognition integration' -Status 'FAIL' `
            -Detail "$($violations -join ', '); $($selected.File.FullName)"
        throw "Voice-recognition evidence violations: $($violations -join ', ')"
    }
    Add-Result -Name 'Deterministic voice-recognition integration' -Status 'PASS_SCOPED' `
        -Detail (
            '3/3 API 36 SpeechRecognizer binder cases passed; app/provider UID RX/TX 0/0; ' +
            "controlled transcript log occurrences 0; $($selected.File.FullName)"
        )
}

function Assert-VoicePermissionDisclosureEvidence {
    $evidenceRoot = Join-Path $repositoryRoot 'docs\evidence\verification\emulator-voice'
    $summaryFiles = @(
        Get-ChildItem -Path $evidenceRoot -Filter 'summary.json' -Recurse `
            -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending
    )
    $selected = $null
    foreach ($summaryFile in $summaryFiles) {
        $candidate = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
        if ($candidate.overall -eq 'PASS' -and
            $null -ne $candidate.disclosure -and
            $null -ne $candidate.systemPermission -and
            $null -ne $candidate.providerAfterGrant) {
            $selected = [pscustomobject]@{ File = $summaryFile; Data = $candidate }
            break
        }
    }
    if ($null -eq $selected) {
        Add-Result -Name 'Voice permission disclosure' -Status 'FAIL' -Detail $evidenceRoot
        throw 'Voice permission disclosure evidence is missing.'
    }

    $summary = $selected.Data
    $disclosure = $summary.disclosure
    $systemPermission = $summary.systemPermission
    $provider = $summary.providerAfterGrant
    $violations = @(
        if ($disclosure.requestingPermissionState -ne 'RequestingPermission') {
            'requestingPermissionState'
        }
        if (-not $disclosure.titlePresent) { 'title' }
        if (-not $disclosure.providerNetworkDisclosurePresent) { 'networkDisclosure' }
        if (-not $disclosure.reviewAndNoAutoSendDisclosurePresent) { 'reviewDisclosure' }
        if (-not $disclosure.fieldChangeFailsClosedToIdle) { 'fieldChangeReset' }
        if (-not $disclosure.dismissLeavesPermissionDenied) { 'dismissGrantState' }
        if (-not $systemPermission.grantedAfterExplicitSystemAction) { 'systemGrant' }
        if ($provider.state -notin @('Listening', 'Processing', 'Result', 'Error')) {
            'providerState'
        }
        if ($provider.error -in @(
                'PermissionDenied', 'NoRecognitionService', 'Disabled', 'DisabledForField'
            )) {
            'providerNotExercised'
        }
        if (-not $provider.restoredIdleAfterCancel) { 'cancelRestore' }
        foreach ($path in @(
            $disclosure.dismissScreenshot,
            $disclosure.grantScreenshot,
            $systemPermission.promptScreenshot
        )) {
            if ([string]::IsNullOrWhiteSpace([string]$path) -or
                -not (Test-Path -LiteralPath $path)) {
                'missingScreenshot'
            }
        }
    )
    if ($violations.Count -gt 0) {
        Add-Result -Name 'Voice permission disclosure' -Status 'FAIL' `
            -Detail "$($violations -join ', '); $($selected.File.FullName)"
        throw "Voice permission disclosure violations: $($violations -join ', ')"
    }
    Add-Result -Name 'Voice permission disclosure' -Status 'PASS_SCOPED' -Detail (
        'API 36 dismiss/retry/system-grant flow passed; disclosure covers provider network, ' +
        'review, and no auto-send; real provider starts after grant and cancel restores Idle; ' +
        $selected.File.FullName
    )
}

function Assert-RealProviderControlledAudioEvidence {
    $evidenceRoot = Join-Path $repositoryRoot `
        'docs\evidence\verification\emulator-connected'
    $summaryFiles = @(
        Get-ChildItem -Path $evidenceRoot -Filter 'summary.json' -Recurse `
            -ErrorAction SilentlyContinue |
            Where-Object { $_.Directory.Name -eq 'real-provider-audio' } |
            Sort-Object LastWriteTime -Descending
    )
    $selected = $null
    foreach ($summaryFile in $summaryFiles) {
        $candidate = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
        if ($candidate.overall -eq 'PASS' -and $candidate.state -eq 'Result' -and
            $null -ne $candidate.audio -and $null -ne $candidate.restoration) {
            $selected = [pscustomobject]@{ File = $summaryFile; Data = $candidate }
            break
        }
    }
    if ($null -eq $selected) {
        Add-Result -Name 'Configured-provider controlled audio' -Status 'FAIL' `
            -Detail $evidenceRoot
        throw 'Configured-provider controlled-audio evidence is missing.'
    }

    $summary = $selected.Data
    $violations = @(
        if ([int]$summary.api -lt 33) { 'api' }
        if (-not [bool]$summary.isEmulator) { 'emulatorIdentity' }
        if ([string]::IsNullOrWhiteSpace([string]$summary.providerComponent)) {
            'providerComponent'
        }
        if (-not [bool]$summary.audio.transferredByteExact) { 'fixtureTransfer' }
        if ([long]$summary.audio.bytes -ne [long]$summary.audio.deviceBytes) {
            'fixtureBytes'
        }
        if ([string]$summary.audio.sha256 -ne [string]$summary.audio.deviceSha256) {
            'fixtureHash'
        }
        if (@($summary.missingTokens).Count -ne 0) { 'transcriptTokens' }
        if (-not [bool]$summary.reviewRequired) { 'reviewRequired' }
        if (-not [bool]$summary.editorTextHashUnchanged) { 'editorChanged' }
        if ([int]$summary.appAttribution.canaryOccurrencesInUidLog -ne 0) {
            'appTranscriptLog'
        }
        if ([int]$summary.providerAttribution.canaryOccurrencesInUidLog -ne 0) {
            'providerTranscriptLog'
        }
        if (-not [bool]$summary.restoration.recordAudioPermissionExact -or
            -not [bool]$summary.restoration.showImeWithHardKeyboardExact -or
            -not [bool]$summary.restoration.defaultImeExact -or
            -not [bool]$summary.restoration.privateAudioFixtureDeleted) {
            'restoration'
        }
    )
    if ($violations.Count -gt 0) {
        Add-Result -Name 'Configured-provider controlled audio' -Status 'FAIL' `
            -Detail "$($violations -join ', '); $($selected.File.FullName)"
        throw "Configured-provider audio evidence violations: $($violations -join ', ')"
    }
    Add-Result -Name 'Configured-provider controlled audio' -Status 'PASS_SCOPED' `
        -Detail (
            'byte-exact PCM produced a review-only transcript through the configured provider; ' +
            'editor unchanged, transcript logs zero, and device state restored; ' +
            'physical microphone/acoustic quality remains open; ' + $selected.File.FullName
        )
}

function Test-PhysicalUpgradePreservation {
    $summaryPath = Join-Path $repositoryRoot `
        'docs\evidence\verification\xiaomi-attended\latest\upgrade-preservation\summary.json'
    if (-not (Test-Path -LiteralPath $summaryPath)) { return $false }
    $upgradeFixtureApk = Join-Path $repositoryRoot `
        'app\build\outputs\apk\upgradeFixture\app-upgradeFixture.apk'
    $benchmarkApk = Join-Path $repositoryRoot `
        'app\build\outputs\apk\benchmark\app-benchmark.apk'
    if (-not (Test-Path -LiteralPath $upgradeFixtureApk) -or
        -not (Test-Path -LiteralPath $benchmarkApk)) {
        return $false
    }
    try {
        $summary = Get-Content -Raw -LiteralPath $summaryPath | ConvertFrom-Json
        $upgradeFixtureHash = (Get-FileHash -Algorithm SHA256 `
            -LiteralPath $upgradeFixtureApk).Hash
        $benchmarkHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $benchmarkApk).Hash
        return $summary.overall -eq 'PASS' -and
            -not [bool]$summary.isEmulator -and
            -not [string]::IsNullOrWhiteSpace([string]$summary.serial) -and
            -not [string]::IsNullOrWhiteSpace([string]$summary.buildFingerprint) -and
            [bool]$summary.packageReplacementObserved -and
            [bool]$summary.versionCodeUpgradeObserved -and
            [long]$summary.afterPackage.versionCode -gt [long]$summary.beforePackage.versionCode -and
            [bool]$summary.legacySnippetPreserved -and
            [bool]$summary.imageUrlsPreserved -and
            [bool]$summary.pinnedClipboardPreserved -and
            [bool]$summary.keyboardSettingsPreserved -and
            [bool]$summary.versionedExportValidated -and
            [bool]$summary.versionedExportRoundTripExact -and
            [bool]$summary.originalStateRestoredExactly -and
            ([string]$summary.beforePackage.installedBaseApkSha256).ToUpperInvariant() -eq
                $upgradeFixtureHash -and
            ([string]$summary.afterPackage.installedBaseApkSha256).ToUpperInvariant() -eq
                $benchmarkHash
    } catch {
        return $false
    }
}

function Assert-ReleaseEvidenceReady {
    $releaseBlockers = @(
        [pscustomobject]@{ id = 'second-physical-device'; detail = 'Lower-midrange and current-mainstream physical-device pair is incomplete.' }
        [pscustomobject]@{ id = 'api-device-app-editor-matrix'; detail = 'Fresh current-suite API 26/30/31/34/35/36 emulator compatibility, core connected tests, declared contracts, local contenteditable/incomplete connections, API 36 device/navigation profiles, Chrome URL/search, Google Messages search/unsent SMS compose, deterministic and official KeePassDX 4.4.5 cross-UID AutofillService-to-SellerKeyboard inline selections with exact two-field fills, native tablet portrait/landscape visuals, 600 dp foldable-width resize/recovery, Pixel Fold OPENED/HALF_OPENED/CLOSED/reopened hinge transitions, verified low-memory background-process death/recreation, and clean-emulator proof that installed Gmail/Docs expose no account-free editor while Google Autofill has no approved credential fixture are recorded; physical foldable acceptance plus product-confirmed multiline messaging, commerce, account-backed email, alternate production password-manager, and rich-content target apps remain incomplete.' }
        [pscustomobject]@{ id = 'accessibility-matrix'; detail = 'API 33 font/reduced-animation evidence plus API 36 TalkBack host-gesture and two-switch USB-uinput traversal/actions with exact key activation are recorded; the emulator reports the switch as external, but a real physical external keyboard/peripheral and remaining physical accessibility matrix are incomplete.' }
        [pscustomobject]@{ id = 'privacy-network-log-matrix'; detail = 'API 33 physical normal typing plus API 36 plain/password/OTP, personal-dictionary add/persist/suggest/sensitive/remove/clear, emoji search/variant/recents/clear/sensitive, clipboard, seller, local/remote-image, deterministic voice result/error/cancel binder paths, voice disclosure dismiss/retry/system-grant, byte-exact configured-provider controlled-audio review, translation-provider, deferred-sync cold-boundary, named Chrome/Messages attribution, and official KeePassDX local-vault Autofill attribution are recorded with zero controlled-text leakage; physical-OEM permission/provider acceptance, real physical-microphone/acoustic quality, production-host, and independent capture remain incomplete.' }
        if (-not (Test-PhysicalUpgradePreservation)) {
            [pscustomobject]@{ id = 'physical-upgrade-backup'; detail = 'Representative physical version-code upgrade preservation of a legacy reply/body/image, pinned clipboard item, keyboard settings, versioned export, and exact rollback is incomplete or stale for the current prepared APK hashes.' }
        }
        [pscustomobject]@{ id = 'product-decisions'; detail = 'The strict sellerkeyboard-product-decisions-v1 approval contract is absent or invalid; reference targets, thresholds, dictionary, voice-network, cache/sync, compact layout, language switching, stale-product policy, and test-account policy remain unapproved.' }
    ) | Where-Object { $null -ne $_ }
    $releaseReadinessPath = Join-Path $reportRoot 'release-readiness.json'
    $releaseReadiness = @(
        $releaseBlockers | ForEach-Object {
            [pscustomobject]@{
                id = $_.id
                detail = $_.detail
                approvedException = if ($_.id -eq 'product-decisions') {
                    Test-ProductDecisionApproval
                } else {
                    Test-ApprovedException -ExceptionId $_.id
                }
            }
        }
    )
    $releaseReadiness | ConvertTo-Json | Set-Content -Encoding utf8 $releaseReadinessPath
    $unapprovedReleaseBlockers = @($releaseReadiness | Where-Object { -not $_.approvedException })
    if ($unapprovedReleaseBlockers.Count -gt 0) {
        Add-Result -Name 'Release evidence readiness' -Status 'BLOCKING_SKIP' -Detail $releaseReadinessPath
        throw "Automated gates passed, but release-blocking evidence is missing. See $releaseReadinessPath"
    }
    Add-Result -Name 'Release evidence readiness' -Status 'APPROVED_EXCEPTION' -Detail $releaseReadinessPath
}

function Test-ProductDecisionApproval {
    if ([string]::IsNullOrWhiteSpace($ProductDecisionApprovalPath)) {
        return $false
    }
    $resolved = Resolve-Path -LiteralPath $ProductDecisionApprovalPath -ErrorAction SilentlyContinue
    if (-not $resolved) {
        return $false
    }
    try {
        $document = Get-Content -Raw -LiteralPath $resolved | ConvertFrom-Json
        if ([int]$document.schemaVersion -ne 1 -or
            $document.contract -ne 'sellerkeyboard-product-decisions-v1' -or
            $document.status -ne 'APPROVED' -or
            [string]::IsNullOrWhiteSpace([string]$document.approval.approvedBy) -or
            [string]::IsNullOrWhiteSpace([string]$document.approval.approvedAt)) {
            return $false
        }
        $approvalTimestamp = [datetimeoffset]::MinValue
        if (-not [datetimeoffset]::TryParse(
            [string]$document.approval.approvedAt,
            [ref]$approvalTimestamp
        )) {
            return $false
        }

        $requiredIds = @(
            'reference-target-matrix',
            'candidate-workflow-thresholds',
            'voice-provider-network',
            'dictionary-licensing',
            'product-image-cache-sync',
            'optional-template-sync',
            'compact-one-handed-geometry',
            'language-switching',
            'stale-product-policy',
            'seller-app-test-accounts'
        )
        $decisions = @($document.decisions)
        if ($decisions.Count -ne $requiredIds.Count -or
            @($decisions | Select-Object -ExpandProperty id -Unique).Count -ne $requiredIds.Count) {
            return $false
        }
        foreach ($requiredId in $requiredIds) {
            $decision = @($decisions | Where-Object { $_.id -eq $requiredId })
            if ($decision.Count -ne 1 -or $decision[0].status -ne 'APPROVED') {
                return $false
            }
            $serializedSelection = $decision[0].selection | ConvertTo-Json -Depth 8 -Compress
            if ([string]::IsNullOrWhiteSpace($serializedSelection) -or
                $serializedSelection -match 'REPLACE_WITH_|needs confirmation') {
                return $false
            }
        }

        $reference = ($decisions | Where-Object id -eq 'reference-target-matrix').selection
        $devices = @($reference.physicalDevices)
        $requiredAppRoles = @(
            'browser', 'multilineMessaging', 'sellerMessaging', 'commerce', 'email',
            'autofillBrowser', 'richContentAccepting', 'richContentRejecting'
        )
        if ($devices.Count -lt 2 -or
            @($devices | Where-Object {
                [string]::IsNullOrWhiteSpace([string]$_.model) -or
                [string]::IsNullOrWhiteSpace([string]$_.androidApi) -or
                [string]::IsNullOrWhiteSpace([string]$_.role)
            }).Count -ne 0 -or
            @($requiredAppRoles | Where-Object {
                [string]::IsNullOrWhiteSpace([string]$reference.apps.$_)
            }).Count -ne 0) {
            return $false
        }

        $thresholds = ($decisions | Where-Object id -eq 'candidate-workflow-thresholds').selection
        if ([double]$thresholds.minimumIndependentTop3Recall -ne 0.85 -or
            [double]$thresholds.maximumProtectedTokenMutationRate -ne 0.0 -or
            [double]$thresholds.requiredAutocorrectionUndoCorrectness -ne 1.0 -or
            [double]$thresholds.minimumSellerInteractionReduction -ne 0.5) {
            return $false
        }
        $voice = ($decisions | Where-Object id -eq 'voice-provider-network').selection
        if (-not $voice.onDevicePreferred -or -not $voice.allowProviderNetworkFallback -or
            -not $voice.requiresExplicitVoiceTap -or
            -not $voice.requiresPrePermissionDisclosure -or
            -not $voice.disabledInSensitiveOrPrivateEditors -or $voice.autoInsertOrSend) {
            return $false
        }
        $dictionary = ($decisions | Where-Object id -eq 'dictionary-licensing').selection
        if ($dictionary.bundledSource -ne 'project-authored-seller-lexicon' -or
            $dictionary.license -ne 'MIT' -or -not $dictionary.includeUserPersonalDictionary -or
            $dictionary.allowUnreviewedThirdPartyCorpus) {
            return $false
        }
        $image = ($decisions | Where-Object id -eq 'product-image-cache-sync').selection
        if ($image.allowedSource -ne 'seller-owned-or-authorized-https' -or
            [int]$image.maximumSharedCacheFiles -ne 5 -or $image.eviction -ne 'oldest-first' -or
            $image.retention -ne 'until-displaced-or-cache-cleared' -or
            $image.syncImagesInCurrentRelease -or -not $image.preserveReviewedTextLinkFallback) {
            return $false
        }
        $sync = ($decisions | Where-Object id -eq 'optional-template-sync').selection
        if ($sync.shipInCurrentRelease -or -not $sync.futureEnablementRequiresSeparateApproval) {
            return $false
        }
        $geometry = ($decisions | Where-Object id -eq 'compact-one-handed-geometry').selection
        if ([int]$geometry.standardMinimumRowDp -ne 48 -or
            [int]$geometry.compactPortraitRowDp -ne 44 -or
            [int]$geometry.compactLandscapeRowDp -ne 40 -or
            [double]$geometry.oneHandedWidthFraction -ne 0.78 -or
            -not $geometry.compactRequiresExplicitUserSelection -or
            -not $geometry.physicalAccessibilityAcceptanceStillRequired) {
            return $false
        }
        $language = ($decisions | Where-Object id -eq 'language-switching').selection
        if (-not $language.androidSubtypeOrPlatformSwitch -or
            -not $language.bilingualLongPressSpaceToggle -or $language.dedicatedGlobeKey) {
            return $false
        }
        $stale = ($decisions | Where-Object id -eq 'stale-product-policy').selection
        if ([int]$stale.staleAfterDays -ne 30 -or -not $stale.showReviewWarning -or
            -not $stale.allowExplicitInsertionAfterReview -or
            $stale.autoRefreshOrFabricate -or $stale.autoSend) {
            return $false
        }
        $accounts = ($decisions | Where-Object id -eq 'seller-app-test-accounts').selection
        if (-not $accounts.dedicatedNonProductionAccountsOnly -or
            -not $accounts.fictionalCustomerAndOrderDataOnly -or
            $accounts.productionCustomerDataAllowed -or
            -not $accounts.missingAccountKeepsNamedAppGateBlocked) {
            return $false
        }
        return $true
    } catch {
        return $false
    }
}

function Test-ApprovedException {
    param([string]$ExceptionId)

    if ([string]::IsNullOrWhiteSpace($ApprovedExceptionPath)) {
        return $false
    }
    $resolved = Resolve-Path -LiteralPath $ApprovedExceptionPath -ErrorAction SilentlyContinue
    if (-not $resolved) {
        return $false
    }
    try {
        $document = Get-Content -Raw -LiteralPath $resolved | ConvertFrom-Json
        if ([int]$document.schemaVersion -ne 1 -or
            $document.contract -ne 'sellerkeyboard-release-exceptions-v1' -or
            $document.status -ne 'APPROVED' -or
            [string]::IsNullOrWhiteSpace([string]$document.approval.approvedBy) -or
            [string]::IsNullOrWhiteSpace([string]$document.approval.approvedAt)) {
            return $false
        }
        $approvalTimestamp = [datetimeoffset]::MinValue
        if (-not [datetimeoffset]::TryParse(
            [string]$document.approval.approvedAt,
            [ref]$approvalTimestamp
        )) {
            return $false
        }
        $matches = @($document.exceptions | Where-Object { $_.id -eq $ExceptionId })
        if ($matches.Count -ne 1 -or $matches[0].status -ne 'APPROVED' -or
            [string]::IsNullOrWhiteSpace([string]$matches[0].rationale) -or
            [string]::IsNullOrWhiteSpace([string]$matches[0].scope) -or
            [string]::IsNullOrWhiteSpace([string]$matches[0].expiresAt)) {
            return $false
        }
        $expiryTimestamp = [datetimeoffset]::MinValue
        if (-not [datetimeoffset]::TryParse(
            [string]$matches[0].expiresAt,
            [ref]$expiryTimestamp
        ) -or $expiryTimestamp -le [datetimeoffset]::Now) {
            return $false
        }
        $serialized = $matches[0] | ConvertTo-Json -Depth 6 -Compress
        return $serialized -notmatch 'REPLACE_WITH_|needs confirmation'
    } catch {
        return $false
    }
}

function Save-Summary {
    param([string]$Overall)

    $summary = [ordered]@{
        generatedAt = (Get-Date).ToString("o")
        repository = $repositoryRoot
        overall = $Overall
        approvedExceptionPath = $ApprovedExceptionPath
        productDecisionApprovalPath = $ProductDecisionApprovalPath
        results = $results
    }
    $summary | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 (Join-Path $reportRoot "summary.json")
    $results | Format-Table -AutoSize | Out-String | Set-Content -Encoding utf8 (Join-Path $reportRoot "summary.txt")
}

if ($MacrobenchmarkOnly) {
    if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
        throw '-MacrobenchmarkOnly requires -DeviceSerial.'
    }
    try {
        if ($ReuseCompletedMacrobenchmark) {
            $completedMacroLog = Join-Path $reportRoot 'connected-macrobenchmark-direct.log'
            if (-not (Test-Path -LiteralPath $completedMacroLog)) {
                throw "Cannot reuse missing Macrobenchmark log: $completedMacroLog"
            }
            $completedMacroText = Get-Content -Raw -LiteralPath $completedMacroLog
            if (-not (Test-CompletedMacrobenchmarkLog -Text $completedMacroText)) {
                throw "Cannot reuse incomplete Macrobenchmark log: $completedMacroLog"
            }
            Copy-CompletedMacrobenchmarkJson -Serial $DeviceSerial -LogPath $completedMacroLog
            Add-Result -Name 'Connected Macrobenchmark' -Status 'PASS_REUSED_COMPLETE_RUN' `
                -Detail "$completedMacroLog; definitive OK (4 tests) footer and required metrics"
        } else {
            Invoke-DirectMacrobenchmark -Serial $DeviceSerial
        }
        Test-MacrobenchmarkThresholds -BenchmarkRoot (
            Join-Path $reportRoot 'connected-macrobenchmark-output'
        )
        Save-Summary -Overall 'PASS'
    } catch {
        Save-Summary -Overall 'FAIL'
        throw
    }
    return
}

if ($EvaluateReleaseReadinessOnly) {
    $existingSummaryPath = Join-Path $reportRoot 'summary.json'
    if (-not (Test-Path -LiteralPath $existingSummaryPath)) {
        throw "Cannot evaluate readiness without an existing automated-gate summary: $existingSummaryPath"
    }
    $existingSummary = Get-Content -Raw -LiteralPath $existingSummaryPath | ConvertFrom-Json
    foreach ($existingResult in $existingSummary.results) {
        if ($existingResult.name -notin @(
            'Emulator API compatibility matrix',
            'Release evidence readiness'
        )) {
            $results.Add([pscustomobject]@{
                name = [string]$existingResult.name
                status = [string]$existingResult.status
                detail = [string]$existingResult.detail
            })
        }
    }
    try {
        Assert-EmulatorApiMatrixEvidence
        Assert-ReleaseEvidenceReady
        Save-Summary -Overall 'PASS'
    } catch {
        Save-Summary -Overall 'FAIL'
        throw
    }
    return
}

try {
    Write-Host "SellerKeyboard competitive release verification"
    Write-Host "Repository: $repositoryRoot"
    Write-Host "Report path: $reportRoot"
    # Java writes its version banner to stderr. Windows PowerShell promotes that
    # normal output to NativeCommandError while ErrorActionPreference is Stop,
    # so normalize it through cmd before applying the release gates.
    $javaVersion = & cmd.exe /d /c "java -version 2>&1" | Select-Object -First 1
    Write-Host "Java: $javaVersion"

    Invoke-GradleGate -Name "Unit tests" -Tasks @(':app:testDebugUnitTest')
    $unitResultRoot = Join-Path $repositoryRoot "app\build\test-results\testDebugUnitTest"
    $unitResultFiles = @(Get-ChildItem -Path $unitResultRoot -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
    $unitInventory = [ordered]@{ suites = $unitResultFiles.Count; tests = 0; failures = 0; errors = 0; skipped = 0 }
    foreach ($unitResultFile in $unitResultFiles) {
        [xml]$unitResult = Get-Content -Raw -LiteralPath $unitResultFile.FullName
        $unitInventory.tests += [int]$unitResult.testsuite.tests
        $unitInventory.failures += [int]$unitResult.testsuite.failures
        $unitInventory.errors += [int]$unitResult.testsuite.errors
        $unitInventory.skipped += [int]$unitResult.testsuite.skipped
    }
    $unitInventoryPath = Join-Path $reportRoot "unit-test-inventory.json"
    $unitInventory | ConvertTo-Json | Set-Content -Encoding utf8 $unitInventoryPath
    if ($unitInventory.tests -eq 0 -or $unitInventory.failures -ne 0 -or
        $unitInventory.errors -ne 0 -or $unitInventory.skipped -ne 0
    ) {
        Add-Result -Name "Unit test inventory" -Status "FAIL" -Detail $unitInventoryPath
        throw "Unit test inventory was empty or contained failures/errors/skips."
    }
    Add-Result -Name "Unit test inventory" -Status "PASS" -Detail (
        "$($unitInventory.suites) suites; $($unitInventory.tests) tests; " +
        "zero failures/errors/skips; $unitInventoryPath"
    )
    Invoke-GradleGate -Name "Candidate corpus" -Tasks @(':app:candidateCorpusTest')
    Invoke-GradleGate -Name "Seller workflow benchmark" -Tasks @(':app:sellerWorkflowBenchmark')
    Assert-SellerWorkflowComparisonEvidence
    Assert-NamedAppEditorEvidence
    Assert-AccountGatedTargetBoundaryEvidence
    Assert-InlineAutofillPlatformEvidence
    Assert-NamedPasswordManagerAutofillEvidence
    Assert-EmulatorAccessibilityEvidence
    Assert-EmulatorLargeWindowEvidence
    Assert-EmulatorFoldablePostureEvidence
    Assert-EmulatorLowMemoryEvidence
    Assert-EmulatorApiMatrixEvidence
    Assert-ProductImageSourcePolicyEvidence
    Assert-PersonalDictionaryEvidence
    Assert-EmojiEvidence
    Assert-DeterministicVoiceRecognitionEvidence
    Assert-VoicePermissionDisclosureEvidence
    Assert-RealProviderControlledAudioEvidence
    Invoke-GradleGate -Name "Key dispatch performance" -Tasks @(':app:keyDispatchPerformanceTest')
    Invoke-GradleGate -Name "Debug APK" -Tasks @(':app:assembleDebug')
    Invoke-GradleGate -Name "Instrumented test APK" -Tasks @(':app:assembleDebugAndroidTest')
    Invoke-GradleGate -Name "Autofill platform fixture APK" `
        -Tasks @(':autofill-test-provider:assembleDebug')
    Invoke-GradleGate -Name "Android lint" `
        -Tasks @(':app:lintDebug', ':autofill-test-provider:lintDebug')
    Invoke-GradleGate -Name "Macrobenchmark APK" -Tasks @(
        ':app:assembleBenchmark',
        ':benchmark-host:assembleBenchmark',
        ':benchmark:assembleBenchmark'
    )

    $privacyAuditLog = Join-Path $reportRoot "privacy-static-audit.txt"
    $sourceFiles = Get-ChildItem -Path (Join-Path $repositoryRoot "app\src\main") -Recurse -File |
        Where-Object { $_.Extension -in @('.kt', '.java', '.xml') }
    $loggingViolations = @(
        $sourceFiles | Select-String -Pattern 'printStackTrace\(|Log\.(v|d|i)\('
    )
    $accessibilityViolations = @(
        $sourceFiles | Select-String -Pattern 'AccessibilityService'
    )
    $manifestText = Get-Content -Raw (Join-Path $repositoryRoot "app\src\main\AndroidManifest.xml")
    $auditLines = @(
        "text-bearing/debug logging candidates=$($loggingViolations.Count)"
        "accessibility service references=$($accessibilityViolations.Count)"
        "automatic cloud backup disabled=$($manifestText -match 'android:allowBackup="false"')"
    )
    $auditLines | Set-Content -Encoding utf8 $privacyAuditLog
    if ($loggingViolations.Count -gt 0 -or $accessibilityViolations.Count -gt 0 -or
        $manifestText -notmatch 'android:allowBackup="false"') {
        Add-Result -Name "Static privacy audit" -Status "FAIL" -Detail $privacyAuditLog
        throw "Static privacy audit failed. See $privacyAuditLog"
    }
    Add-Result -Name "Static privacy audit" -Status "PASS" -Detail $privacyAuditLog

    $composeSource = Join-Path $repositoryRoot "app\build\compose_compiler"
    $composeDestination = Join-Path $reportRoot "compose-compiler"
    if (Test-Path $composeSource) {
        New-Item -ItemType Directory -Force -Path $composeDestination | Out-Null
        Copy-Item -Path (Join-Path $composeSource "*") -Destination $composeDestination -Recurse -Force
        Add-Result -Name "Compose compiler reports" -Status "PASS" -Detail $composeDestination
    } else {
        Add-Result -Name "Compose compiler reports" -Status "FAIL" -Detail "Missing $composeSource"
        throw "Compose compiler reports were not generated."
    }

    $unitReportSource = Join-Path $repositoryRoot "app\build\reports\tests\testDebugUnitTest"
    if (Test-Path $unitReportSource) {
        $unitReportDestination = Join-Path $reportRoot "unit-test-report"
        New-Item -ItemType Directory -Force -Path $unitReportDestination | Out-Null
        Copy-Item -Path (Join-Path $unitReportSource "*") -Destination $unitReportDestination -Recurse -Force
    }

    $adbAvailable = (Test-Path -LiteralPath $adb) -or
        [bool](Get-Command $adb -ErrorAction SilentlyContinue)
    $deviceLines = if ($adbAvailable) {
        @(& $adb devices 2>&1)
    } else {
        @("ADB executable is unavailable: $adb")
    }
    $connectedDevices = @($deviceLines | Where-Object { $_ -match "\tdevice$" })
    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
        $connectedDevices = @($connectedDevices | Where-Object {
            ($_ -split "\t")[0] -eq $DeviceSerial
        })
        if ($connectedDevices.Count -eq 0) {
            throw "Requested device '$DeviceSerial' is not connected and authorized."
        }
    } elseif ($connectedDevices.Count -gt 1) {
        throw "Multiple Android devices are connected. Specify -DeviceSerial to prevent installing on the wrong device."
    }
    $deviceLog = Join-Path $reportRoot "device-configuration.txt"

    if ($connectedDevices.Count -gt 0) {
        $serial = ($connectedDevices[0] -split "\t")[0]
        $manufacturer = ((& $adb -s $serial shell getprop ro.product.manufacturer) -join '').Trim()
        $preferDirectInstall = -not [string]::IsNullOrWhiteSpace($DeviceSerial) -or
            $manufacturer -match '^(Xiaomi|Redmi)$'
        $configuration = @(
            "serial=$serial"
            "manufacturer=$manufacturer"
            "model=$(& $adb -s $serial shell getprop ro.product.model)"
            "device=$(& $adb -s $serial shell getprop ro.product.device)"
            "api=$(& $adb -s $serial shell getprop ro.build.version.sdk)"
            "android=$(& $adb -s $serial shell getprop ro.build.version.release)"
            "build=$(& $adb -s $serial shell getprop ro.build.fingerprint)"
            "density=$(& $adb -s $serial shell wm density)"
            "size=$(& $adb -s $serial shell wm size)"
            "refreshRate=$(& $adb -s $serial shell dumpsys display | Select-String -Pattern 'refreshRate' | Select-Object -First 1)"
        )
        $configuration | Tee-Object -FilePath $deviceLog | Write-Host
        Add-Result -Name "Device configuration" -Status "PASS" -Detail $deviceLog
        if ($preferDirectInstall) {
            # MIUI's package-session cleanup can remove the target before an OEM
            # confirmation denial. Direct installs fail without uninstalling the
            # previously working IME and produce a deterministic runner log.
            try {
                Invoke-DirectConnectedTests -Serial $serial
            } catch {
                Add-Result -Name "Connected Android tests" -Status "FAIL" -Detail (
                    Join-Path $reportRoot "connected-android-tests-direct.log"
                )
                throw
            }
        } else {
            $connectedViaGradle = Invoke-GradleGate -Name "Connected Android tests" `
                -Tasks @(':app:connectedDebugAndroidTest') -AllowFailure
            if (-not $connectedViaGradle) {
            $connectedGradleLog = Join-Path $reportRoot "connected-android-tests.log"
            if (-not (Select-String -LiteralPath $connectedGradleLog `
                    -SimpleMatch 'INSTALL_FAILED_USER_RESTRICTED' -Quiet)) {
                Add-Result -Name "Connected Android tests" -Status "FAIL" -Detail $connectedGradleLog
                throw "Connected Android tests failed for a non-OEM-install reason. See $connectedGradleLog"
            }
            try {
                Invoke-DirectConnectedTests -Serial $serial
            } catch {
                Add-Result -Name "Connected Android tests" -Status "FAIL" -Detail (
                    Join-Path $reportRoot "connected-android-tests-direct.log"
                )
                throw
            }
            } else {
            $connectedResultRoot = Join-Path $repositoryRoot "app\build\outputs\androidTest-results"
            $skippedResults = @()
            if (Test-Path $connectedResultRoot) {
                foreach ($resultFile in Get-ChildItem $connectedResultRoot -Recurse -Filter '*.xml') {
                    [xml]$connectedResult = Get-Content -Raw -LiteralPath $resultFile.FullName
                    foreach ($testCase in @($connectedResult.SelectNodes('//testcase[skipped]'))) {
                        $className = [string]$testCase.classname
                        $skippedResults += [pscustomobject]@{
                            ResultPath = [System.IO.Path]::GetRelativePath(
                                $repositoryRoot,
                                $resultFile.FullName
                            )
                            ClassName = $className
                            TestName = [string]$testCase.name
                            Reason = if (
                                $className -eq
                                    'com.terasumi.sellerkeyboard.backend.ConvexBackupConnectedSmokeTest'
                            ) {
                                'Optional live backend smoke test requires instrumentation argument convexSmoke=true.'
                            } else {
                                'JUnit result did not include a skip reason.'
                            }
                        }
                    }
                }
            }
            if ($skippedResults.Count -gt 0) {
                $skipLog = Write-ConnectedSkipReport -SkippedResults $skippedResults
                $onlyOptionalConvex = @(
                    $skippedResults | Where-Object {
                        $_.ClassName -ne 'com.terasumi.sellerkeyboard.backend.ConvexBackupConnectedSmokeTest'
                    }
                ).Count -eq 0
                if ($onlyOptionalConvex) {
                    Add-Result -Name "Optional live backend smoke tests" `
                        -Status "IDENTIFIED_SKIP" `
                        -Detail "$($skippedResults.Count) opt-in Convex tests; $skipLog"
                } elseif (Test-ApprovedException -ExceptionId "connected-test-skips") {
                    Add-Result -Name "Connected test skips" -Status "APPROVED_EXCEPTION" -Detail $skipLog
                } else {
                    Add-Result -Name "Connected test skips" -Status "BLOCKING_SKIP" -Detail $skipLog
                    throw "Connected tests contained unapproved skips. See $skipLog"
                }
            }
            }
        }
        $installedSellerKeyboard = ((& $adb -s $serial shell pm path `
            'com.terasumi.sellerkeyboard' 2>$null) -join "`n") -match '^package:'
        if (-not $installedSellerKeyboard) {
            $postConnectedInstallLog = Join-Path $reportRoot `
                'post-connected-debug-reinstall.log'
            Invoke-AdbCommand -Name 'Post-connected SellerKeyboard reinstall' -Arguments @(
                '-s', $serial, 'install', '-r', '-t',
                (Join-Path $repositoryRoot 'app\build\outputs\apk\debug\app-debug.apk')
            ) -LogPath $postConnectedInstallLog
        }
        # Run privacy against the freshly connected-tested debug build before
        # Macrobenchmark installs the optimized IME and begins force-stop based
        # process isolation. AndroidX benchmark cleanup may otherwise terminate
        # the privacy editor while its UI evidence is being captured.
        Invoke-PrivacyNetworkProbe -Serial $serial
        # The app connected-test task may uninstall its target during UTP cleanup.
        # SellerKeyboard is deliberately not the Macrobenchmark target (the neutral
        # editor host is), so install the release-optimized IME explicitly here.
        $imeInstallLog = Join-Path $reportRoot "macrobenchmark-ime-install.log"
        "Installing optimized SellerKeyboard IME for Macrobenchmark" |
            Set-Content -Encoding utf8 $imeInstallLog
        try {
            Invoke-AdbCommand -Name "Macrobenchmark SellerKeyboard install" -Arguments @(
                '-s', $serial, 'install', '-r',
                (Join-Path $repositoryRoot 'app\build\outputs\apk\benchmark\app-benchmark.apk')
            ) -LogPath $imeInstallLog
        } catch {
            Add-Result -Name "Connected Macrobenchmark" -Status "FAIL" -Detail $imeInstallLog
            throw
        }
        $benchmarkDestination = Join-Path $reportRoot "connected-macrobenchmark-output"
        if ($preferDirectInstall) {
            try {
                $completedMacroLog = Join-Path $reportRoot 'connected-macrobenchmark-direct.log'
                $canReuseMacro = $ReuseCompletedMacrobenchmark -and
                    (Test-Path -LiteralPath $completedMacroLog) -and
                    (Test-CompletedMacrobenchmarkLog -Text (
                        Get-Content -Raw -LiteralPath $completedMacroLog
                    ))
                if ($canReuseMacro) {
                    Copy-CompletedMacrobenchmarkJson -Serial $serial -LogPath $completedMacroLog
                    Add-Result -Name "Connected Macrobenchmark" -Status 'PASS_REUSED_COMPLETE_RUN' `
                        -Detail "$completedMacroLog; definitive OK (4 tests) footer and required metrics"
                } else {
                    Invoke-DirectMacrobenchmark -Serial $serial
                }
            } catch {
                Add-Result -Name "Connected Macrobenchmark" -Status "FAIL" -Detail (
                    Join-Path $reportRoot "connected-macrobenchmark-direct.log"
                )
                throw
            }
        } else {
            $benchmarkViaGradle = Invoke-GradleGate -Name "Connected Macrobenchmark" `
                -Tasks @(':benchmark:connectedBenchmarkAndroidTest') -AllowFailure
            if (-not $benchmarkViaGradle) {
            $benchmarkGradleLog = Join-Path $reportRoot "connected-macrobenchmark.log"
            if (-not (Select-String -LiteralPath $benchmarkGradleLog `
                    -SimpleMatch 'INSTALL_FAILED_USER_RESTRICTED' -Quiet)) {
                Add-Result -Name "Connected Macrobenchmark" -Status "FAIL" -Detail $benchmarkGradleLog
                throw "Connected Macrobenchmark failed for a non-OEM-install reason. See $benchmarkGradleLog"
            }
            try {
                Invoke-DirectMacrobenchmark -Serial $serial
            } catch {
                Add-Result -Name "Connected Macrobenchmark" -Status "FAIL" -Detail (
                    Join-Path $reportRoot "connected-macrobenchmark-direct.log"
                )
                throw
            }
            } else {
            $benchmarkOutputRoot = Join-Path $repositoryRoot "benchmark\build\outputs"
            if (Test-Path $benchmarkOutputRoot) {
                $benchmarkDestination = Join-Path $reportRoot "connected-macrobenchmark-output"
                New-Item -ItemType Directory -Force -Path $benchmarkDestination | Out-Null
                Copy-Item -Path (Join-Path $benchmarkOutputRoot "*") `
                    -Destination $benchmarkDestination -Recurse -Force
            }
            }
        }
        Test-MacrobenchmarkThresholds -BenchmarkRoot $benchmarkDestination
    } else {
        "No authorized Android device was connected." | Set-Content -Encoding utf8 $deviceLog
        $exceptionId = "missing-android-device"
        if (Test-ApprovedException -ExceptionId $exceptionId) {
            Add-Result -Name "Connected device gates" -Status "APPROVED_EXCEPTION" -Detail "$exceptionId; $deviceLog"
        } else {
            Add-Result -Name "Connected Android tests" -Status "BLOCKING_SKIP" -Detail "$exceptionId; $deviceLog"
            Add-Result -Name "Connected Macrobenchmark" -Status "BLOCKING_SKIP" -Detail "$exceptionId; $deviceLog"
            Save-Summary -Overall "FAIL"
            Write-Error "Release-blocking device checks were skipped. Connect a device or provide a valid sellerkeyboard-release-exceptions-v1 record approving '$exceptionId'."
        }
    }

    Assert-ReleaseEvidenceReady

    Save-Summary -Overall "PASS"
    Write-Host "`nAll applicable competitive release gates passed."
} catch {
    Save-Summary -Overall "FAIL"
    throw
}
