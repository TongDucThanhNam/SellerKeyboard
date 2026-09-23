[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$OutputDirectory = "",
    [switch]$ResumeAfterConnectedTests
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
} else {
    "adb"
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repositoryRoot `
        "docs\evidence\verification\xiaomi-attended\latest"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $output = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: $($Arguments -join ' ')`n$($output -join "`n")"
    }
    $output
}

function Get-Setting([string]$Namespace, [string]$Name) {
    ((Adb '-s' $Serial shell settings get $Namespace $Name) -join '').Trim()
}

function Restore-Setting([string]$Namespace, [string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -eq 'null') {
        Adb '-s' $Serial shell settings delete $Namespace $Name | Out-Null
    } else {
        Adb '-s' $Serial shell settings put $Namespace $Name $Value | Out-Null
    }
}

function Assert-PreparedBatch {
    $manifestPath = Join-Path $repositoryRoot `
        'docs\evidence\verification\xiaomi-attended\prepared\manifest.json'
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        throw "Prepared manifest is missing: $manifestPath"
    }
    $manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    if ([int]$manifest.approvalCount -ne 5 -or @($manifest.artifacts).Count -ne 5) {
        throw 'Prepared manifest must describe exactly five install approvals.'
    }
    foreach ($artifact in $manifest.artifacts) {
        $path = Join-Path $repositoryRoot ([string]$artifact.path)
        if (-not (Test-Path -LiteralPath $path)) {
            throw "Prepared APK is missing: $path"
        }
        $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash
        if ($actualHash -ne [string]$artifact.sha256) {
            throw "Prepared APK changed after hashing: $path. Run preparation again."
        }
    }
    if (-not [bool]$manifest.securityBoundary.benchmarkHasShellGuardedBridge -or
        [bool]$manifest.securityBoundary.benchmarkDebuggable -or
        [bool]$manifest.securityBoundary.releaseHasTestBridge -or
        [bool]$manifest.securityBoundary.releaseDebuggable -or
        -not [bool]$manifest.securityBoundary.versionCodeUpgradePrepared -or
        [int]$manifest.securityBoundary.upgradeFixtureVersionCode -ge
            [int]$manifest.securityBoundary.benchmarkVersionCode) {
        throw 'Prepared manifest does not prove the benchmark/release test-bridge boundary.'
    }
}

function Install-Attended(
    [int]$Approval,
    [string]$Name,
    [string]$RelativePath,
    [string]$LogPath
) {
    $path = Join-Path $repositoryRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Prepared APK is missing: $path. Run prepare-xiaomi-attended-batch.ps1 first."
    }
    if ($Approval -ge 4) {
        try { [Console]::Beep(900, 500) } catch { }
    }
    Write-Host ""
    Write-Host "MIUI approval $Approval/5: $Name"
    Write-Host "Press Allow/Install on the Xiaomi prompt now."
    $installArguments = @('-s', $Serial, 'install', '-r', '-t')
    if ($Approval -eq 1) {
        # The prepared source is deliberately versionCode 24 so approval 4 is a
        # real upgrade to the versionCode 25 optimized candidate. It remains
        # debuggable and same-signature, allowing an attended data-preserving
        # downgrade when the phone already has a newer development build.
        $installArguments += '-d'
    }
    $installArguments += $path
    $output = @(& $adb @installArguments 2>&1)
    $output | Tee-Object -FilePath $LogPath -Append | Write-Host
    if ($LASTEXITCODE -ne 0 -or ($output -join "`n") -notmatch 'Success') {
        throw "Install $Approval/5 failed. Keep the phone unlocked and approve the MIUI prompt. See $LogPath"
    }
}

Assert-PreparedBatch

$deviceLine = @(& $adb devices | Where-Object {
    $_ -match "^$([regex]::Escape($Serial))\s+device$"
})
if ($deviceLine.Count -ne 1) {
    throw "Physical device '$Serial' is not connected and authorized."
}
$manufacturer = ((Adb '-s' $Serial shell getprop ro.product.manufacturer) -join '').Trim()
if ($manufacturer -notmatch '^(Xiaomi|Redmi)$') {
    throw "This attended OEM runner is only for Xiaomi/Redmi; found '$manufacturer'."
}
$isEmulator = ((Adb '-s' $Serial shell getprop ro.kernel.qemu) -join '').Trim()
if ($isEmulator -eq '1') {
    throw "Use the zero-touch emulator runner for emulators, not this attended Xiaomi runner."
}
$deviceApi = [int](((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '').Trim())

$installLog = Join-Path $OutputDirectory 'installs.log'
$instrumentationLog = Join-Path $OutputDirectory 'connected-instrumentation.log'
$macroLog = Join-Path $OutputDirectory 'macrobenchmark.log'
$benchmarkRoot = Join-Path $OutputDirectory 'macrobenchmark-output'
New-Item -ItemType Directory -Force -Path $benchmarkRoot | Out-Null

$originalIme = Get-Setting secure default_input_method
$originalBackgroundStarts = Get-Setting global background_activity_starts_enabled
$originalDeviceConfig = ((Adb '-s' $Serial shell device_config get activity_manager `
    default_background_activity_starts_enabled) -join '').Trim()
$upgradeFixtureSeeded = $false

try {
    @(
        "serial=$Serial"
        "manufacturer=$manufacturer"
        "model=$((Adb '-s' $Serial shell getprop ro.product.model) -join '')"
        "device=$((Adb '-s' $Serial shell getprop ro.product.device) -join '')"
        "api=$((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '')"
        "android=$((Adb '-s' $Serial shell getprop ro.build.version.release) -join '')"
        "build=$((Adb '-s' $Serial shell getprop ro.build.fingerprint) -join '')"
    ) | Set-Content -Encoding utf8 (Join-Path $OutputDirectory 'device.txt')

    Adb '-s' $Serial shell settings put global background_activity_starts_enabled 1 | Out-Null
    Adb '-s' $Serial shell device_config put activity_manager `
        default_background_activity_starts_enabled true | Out-Null

    if (-not $ResumeAfterConnectedTests) {
        Install-Attended 1 'SellerKeyboard v24 upgrade fixture target' `
            'app\build\outputs\apk\upgradeFixture\app-upgradeFixture.apk' $installLog
        Install-Attended 2 'SellerKeyboard connected tests' `
            'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk' $installLog
        Install-Attended 3 'Neutral benchmark editor host' `
            'benchmark-host\build\outputs\apk\benchmark\benchmark-host-benchmark.apk' $installLog

        Write-Host "The first three approvals are complete; running all connected tests."
        $connected = @(& $adb -s $Serial shell am instrument -w -r `
            'com.terasumi.sellerkeyboard.test/androidx.test.runner.AndroidJUnitRunner' 2>&1)
        $connectedExit = $LASTEXITCODE
        $connected | Set-Content -Encoding utf8 $instrumentationLog
    } else {
        Write-Host 'Resuming after the recorded connected suite; validating installed packages and log.'
        $installedVersion = ((Adb '-s' $Serial shell dumpsys package `
            com.terasumi.sellerkeyboard) -join "`n")
        if ($installedVersion -notmatch '(?m)^\s*versionCode=24(?:\s|$)') {
            throw 'Resume requires the installed v24 upgrade fixture.'
        }
        foreach ($packageName in @(
            'com.terasumi.sellerkeyboard.test',
            'com.terasumi.sellerkeyboard.benchmarkhost'
        )) {
            $packagePath = (Adb '-s' $Serial shell pm path $packageName) -join ''
            if ($packagePath -notmatch '^package:') {
                throw "Resume requires installed package $packageName."
            }
        }
        if (-not (Test-Path -LiteralPath $instrumentationLog)) {
            throw "Resume requires the recorded connected log: $instrumentationLog"
        }
        $connectedExit = 0
    }
    $connectedText = Get-Content -Raw -LiteralPath $instrumentationLog
    $optionalSkips = ([regex]::Matches(
        $connectedText,
        'INSTRUMENTATION_STATUS_CODE: -4'
    )).Count
    if ($connectedExit -ne 0 -or $connectedText -notmatch 'OK \(22 tests\)' -or
        $connectedText -match 'FAILURES!!!' -or $optionalSkips -ne 2) {
        throw "Connected tests did not produce 22 results with exactly two opt-in live-backend skips. See $instrumentationLog"
    }

    $upgradeRoot = Join-Path $OutputDirectory 'upgrade-preservation'
    & (Join-Path $PSScriptRoot 'verify-upgrade-preservation.ps1') `
        -Serial $Serial -Phase Seed -OutputDirectory $upgradeRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Could not seed the reversible physical upgrade fixture. See $upgradeRoot"
    }
    $upgradeFixtureSeeded = $true

    Write-Host "Connected tests passed; approvals 4/5 now follow back-to-back."
    Install-Attended 4 'SellerKeyboard optimized benchmark target' `
        'app\build\outputs\apk\benchmark\app-benchmark.apk' $installLog
    Install-Attended 5 'Macrobenchmark runner' `
        'benchmark\build\outputs\apk\benchmark\benchmark-benchmark.apk' $installLog

    Write-Host "All five approvals are complete; the remaining evidence capture is automatic."
    & (Join-Path $PSScriptRoot 'verify-upgrade-preservation.ps1') `
        -Serial $Serial -Phase VerifyAndRestore -OutputDirectory $upgradeRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Physical upgrade preservation failed. See $upgradeRoot"
    }
    $upgradeFixtureSeeded = $false
    $upgradePreservation = Get-Content -Raw -LiteralPath `
        (Join-Path $upgradeRoot 'summary.json') | ConvertFrom-Json
    if ($upgradePreservation.overall -ne 'PASS' -or
        -not [bool]$upgradePreservation.packageReplacementObserved -or
        -not [bool]$upgradePreservation.originalStateRestoredExactly) {
        throw "Physical upgrade summary did not pass. See $upgradeRoot"
    }

    $voicePermissionRoot = Join-Path $OutputDirectory 'voice-permission'
    & (Join-Path $PSScriptRoot 'verify-voice-permission-disclosure.ps1') `
        -Serial $Serial -OutputDirectory $voicePermissionRoot -SkipBuild -SkipInstall
    if ($LASTEXITCODE -ne 0) {
        throw "Physical voice permission/disclosure flow failed. See $voicePermissionRoot"
    }
    $voicePermission = Get-Content -Raw -LiteralPath `
        (Join-Path $voicePermissionRoot 'summary.json') | ConvertFrom-Json
    if ($voicePermission.overall -ne 'PASS' -or [bool]$voicePermission.isEmulator -or
        -not [bool]$voicePermission.restoration.recordAudioPermissionExact -or
        -not [bool]$voicePermission.restoration.defaultImeExact) {
        throw "Physical voice permission summary did not pass. See $voicePermissionRoot"
    }

    $providerAudioRoot = Join-Path $OutputDirectory 'real-provider-audio'
    & (Join-Path $PSScriptRoot 'verify-real-provider-audio.ps1') `
        -Serial $Serial -OutputDirectory $providerAudioRoot -SkipBuild -SkipInstall
    if ($LASTEXITCODE -ne 0) {
        throw "Physical configured-provider audio flow failed. See $providerAudioRoot"
    }
    $providerAudio = Get-Content -Raw -LiteralPath `
        (Join-Path $providerAudioRoot 'summary.json') | ConvertFrom-Json
    if ($providerAudio.overall -ne 'PASS' -or [bool]$providerAudio.isEmulator -or
        $providerAudio.state -ne 'Result' -or -not [bool]$providerAudio.reviewRequired -or
        -not [bool]$providerAudio.editorTextHashUnchanged -or
        -not [bool]$providerAudio.restoration.privateAudioFixtureDeleted) {
        throw "Physical configured-provider audio summary did not pass. See $providerAudioRoot"
    }

    $emojiRoot = Join-Path $OutputDirectory 'emoji-flow'
    & (Join-Path $PSScriptRoot 'verify-emoji-flow.ps1') `
        -Serial $Serial -OutputDirectory $emojiRoot -ExpectedApi $deviceApi -SkipBuild
    if ($LASTEXITCODE -ne 0) {
        throw "Physical emoji flow failed. See $emojiRoot"
    }
    $emojiFlow = Get-Content -Raw -LiteralPath `
        (Join-Path $emojiRoot 'summary.json') | ConvertFrom-Json
    if ($emojiFlow.overall -ne 'PASS') {
        throw "Physical emoji summary did not pass. See $emojiRoot"
    }

    $coreInteractionsRoot = Join-Path $OutputDirectory 'core-interactions'
    & (Join-Path $PSScriptRoot 'verify-connected-core-interactions.ps1') `
        -Serial $Serial -OutputDirectory $coreInteractionsRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Physical core Shift/gesture interactions failed. See $coreInteractionsRoot"
    }
    $coreInteractions = Get-Content -Raw -LiteralPath `
        (Join-Path $coreInteractionsRoot 'summary.json') | ConvertFrom-Json
    if ($coreInteractions.overall -ne 'PASS') {
        throw "Physical core Shift/gesture summary did not pass. See $coreInteractionsRoot"
    }

    $macro = @(& $adb -s $Serial shell am instrument -w -r `
        'com.terasumi.sellerkeyboard.benchmark/androidx.test.runner.AndroidJUnitRunner' 2>&1)
    $macroExit = $LASTEXITCODE
    $macro | Set-Content -Encoding utf8 $macroLog
    $macroText = $macro -join "`n"
    if ($macroExit -ne 0 -or $macroText -notmatch 'OK \(4 tests\)' -or
        $macroText -match 'FAILURES!!!' -or
        $macroText -notmatch 'SellerKeyboard\.keyDispatchMaxMs' -or
        $macroText -notmatch 'SellerKeyboard\.candidateQueryMaxMs' -or
        $macroText -notmatch 'SellerKeyboard\.candidateDisplayMaxMs' -or
        $macroText -notmatch 'SellerKeyboard\.surfaceSwitchMaxMs' -or
        $macroText -notmatch 'SellerKeyboard\.templateInsertionMaxMs') {
        throw "Macrobenchmark did not produce four passing tests and all production-flow traces. See $macroLog"
    }
    Adb '-s' $Serial pull `
        '/storage/emulated/0/Android/media/com.terasumi.sellerkeyboard.benchmark/com.terasumi.sellerkeyboard.benchmark-benchmarkData.json' `
        (Join-Path $benchmarkRoot 'benchmarkData.json') | Out-Null

    $data = Get-Content -Raw -LiteralPath `
        (Join-Path $benchmarkRoot 'benchmarkData.json') | ConvertFrom-Json
    $seller = $data.benchmarks | Where-Object {
        $_.name -eq 'coldEditorLaunchAndSellerKeyboardImeShow'
    }
    $gboard = $data.benchmarks | Where-Object {
        $_.name -eq 'coldEditorLaunchAndGboardImeShowReference'
    }
    $stress = $data.benchmarks | Where-Object {
        $_.name -eq 'oneHundredImeShowHideCyclesTrackFramesAndMemory'
    }
    $sellerMedian = [double]$seller.metrics.'SellerKeyboard.ImeShowToInteractiveFirstMs'.median
    $gboardMedian = [double]$gboard.metrics.'SellerKeyboard.ImeShowToInteractiveFirstMs'.median
    $ratio = $sellerMedian / $gboardMedian
    $frameMaximum = 0.0
    foreach ($run in $stress.sampledMetrics.frameDurationCpuMs.runs) {
        foreach ($sample in $run) {
            $frameMaximum = [Math]::Max($frameMaximum, [double]$sample)
        }
    }
    if ($ratio -gt 1.25 -or $frameMaximum -gt 700.0) {
        throw "Physical performance threshold failed: ratio=$ratio, frameMaximumMs=$frameMaximum"
    }

    $privacyRoot = Join-Path $OutputDirectory 'privacy-network-probe'
    & (Join-Path $PSScriptRoot 'capture-privacy-network-evidence.ps1') `
        -Serial $Serial -OutputDirectory $privacyRoot
    if ($LASTEXITCODE -ne 0) { throw "Physical privacy probe failed. See $privacyRoot" }

    $matrixRoot = Join-Path $OutputDirectory 'device-matrix'
    & (Join-Path $PSScriptRoot 'capture-api33-device-matrix.ps1') `
        -Serial $Serial -OutputDirectory $matrixRoot
    if ($LASTEXITCODE -ne 0) { throw "Physical device matrix failed. See $matrixRoot" }

    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        manufacturer = $manufacturer
        approvals = 5
        connectedResults = 22
        optionalBackendSkips = 2
        macrobenchmarkTests = 4
        sellerVisibleImeMedianMs = $sellerMedian
        gboardVisibleImeMedianMs = $gboardMedian
        sellerToGboardRatio = $ratio
        stressFrameMaximumMs = $frameMaximum
        coreInteractions = $coreInteractionsRoot
        oneShotShiftVerified = [bool]$coreInteractions.oneShotShiftVerified
        capsLockDoubleTapVerified = [bool]$coreInteractions.capsLockDoubleTapVerified
        punctuationAutoCapsVerified = [bool]$coreInteractions.punctuationAutoCapsVerified
        spaceCursorMoveVerified = [bool]$coreInteractions.spaceCursorMoveVerified
        spacePointerExitRestoreVerified = [bool]$coreInteractions.spacePointerExitRestoreVerified
        swipeDeleteVerified = [bool]$coreInteractions.swipeDeleteVerified
        swipeDeletePointerExitRestoreVerified = `
            [bool]$coreInteractions.swipeDeletePointerExitRestoreVerified
        holdDeleteRepeatVerified = [bool]$coreInteractions.holdDeleteRepeatVerified
        holdDeleteStopsOnReleaseVerified = [bool]$coreInteractions.holdDeleteStopsOnReleaseVerified
        emojiFlow = $emojiRoot
        emojiTouchOnlySearchVerified = [bool]$emojiFlow.touchOnlySearchVerified
        emojiVariantCommitExact = [bool]$emojiFlow.variantCommitExact
        emojiRecentsPersistenceVerified = `
            [bool]$emojiFlow.recentsPersistenceAfterProcessRecreationVerified
        emojiClearPersistenceVerified = `
            [bool]$emojiFlow.clearPersistenceAfterProcessRecreationVerified
        emojiSensitiveSuppressionVerified = [bool]$emojiFlow.sensitiveFieldSuppressionVerified
        emojiUidRxDelta = [long]$emojiFlow.uidRxDelta
        emojiUidTxDelta = [long]$emojiFlow.uidTxDelta
        upgradePreservation = $upgradeRoot
        packageReplacementObserved = `
            [bool]$upgradePreservation.packageReplacementObserved
        legacySnippetPreserved = [bool]$upgradePreservation.legacySnippetPreserved
        imageUrlsPreserved = [bool]$upgradePreservation.imageUrlsPreserved
        pinnedClipboardPreserved = [bool]$upgradePreservation.pinnedClipboardPreserved
        keyboardSettingsPreserved = [bool]$upgradePreservation.keyboardSettingsPreserved
        versionedExportRoundTripExact = `
            [bool]$upgradePreservation.versionedExportRoundTripExact
        upgradeOriginalStateRestoredExactly = `
            [bool]$upgradePreservation.originalStateRestoredExactly
        voicePermission = $voicePermissionRoot
        voicePermissionDisclosureVerified = $true
        voiceSystemPermissionVerified = $true
        voicePermissionStateRestoredExactly = `
            [bool]$voicePermission.restoration.recordAudioPermissionExact
        realProviderAudio = $providerAudioRoot
        realProviderComponent = [string]$providerAudio.providerComponent
        realProviderControlledAudioResultVerified = $true
        realProviderReviewRequired = [bool]$providerAudio.reviewRequired
        realProviderEditorUnchanged = [bool]$providerAudio.editorTextHashUnchanged
        realProviderStateRestoredExactly = `
            [bool]$providerAudio.restoration.recordAudioPermissionExact
        privacyProbe = $privacyRoot
        deviceMatrix = $matrixRoot
        remainingManualGates = @(
            'product-confirmed messaging, commerce, email, browser, and rich-content apps',
            'visible physical TalkBack and Switch Access acceptance',
            'physical external keyboard/peripheral and broader accessibility matrix',
            'second current-mainstream physical reference device',
            'independent privacy capture and production-host/provider acceptance',
            'product-owner decisions'
        )
    } | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'summary.json')
    Write-Host "Xiaomi attended batch passed: $OutputDirectory"
} finally {
    if ($upgradeFixtureSeeded) {
        try {
            & (Join-Path $PSScriptRoot 'verify-upgrade-preservation.ps1') `
                -Serial $Serial -Phase Restore `
                -OutputDirectory (Join-Path $OutputDirectory 'upgrade-preservation')
        } catch {
            Write-Warning "Emergency upgrade-fixture restore failed: $($_.Exception.Message)"
        }
    }
    Restore-Setting global background_activity_starts_enabled $originalBackgroundStarts
    if ([string]::IsNullOrWhiteSpace($originalDeviceConfig) -or
        $originalDeviceConfig -eq 'null') {
        Adb '-s' $Serial shell device_config delete activity_manager `
            default_background_activity_starts_enabled | Out-Null
    } else {
        Adb '-s' $Serial shell device_config put activity_manager `
            default_background_activity_starts_enabled $originalDeviceConfig | Out-Null
    }
    if (-not [string]::IsNullOrWhiteSpace($originalIme) -and $originalIme -ne 'null') {
        Adb '-s' $Serial shell ime set $originalIme | Out-Null
    }
}
