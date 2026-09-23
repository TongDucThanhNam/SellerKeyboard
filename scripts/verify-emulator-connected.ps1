[CmdletBinding()]
param(
    [string]$Serial = "",
    [string]$OutputDirectory = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$reportRoot = if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    Join-Path $root "docs\evidence\verification\emulator-connected\latest"
} else {
    $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDirectory)
}
$gradle = Join-Path $root "gradlew.bat"
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" } else { "adb" }
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null

$devices = @(& $adb devices | Where-Object { $_ -match '^emulator-\d+\s+device$' })
if ([string]::IsNullOrWhiteSpace($Serial)) {
    if ($devices.Count -ne 1) { throw "Specify -Serial unless exactly one Android emulator is connected." }
    $Serial = ($devices[0] -split "\s+")[0]
}
if (-not ($devices | Where-Object { ($_ -split "\s+")[0] -eq $Serial })) {
    throw "Emulator '$Serial' is not connected and authorized."
}

$configuration = [ordered]@{
    serial = $Serial
    manufacturer = ((& $adb -s $Serial shell getprop ro.product.manufacturer) -join '').Trim()
    model = ((& $adb -s $Serial shell getprop ro.product.model) -join '').Trim()
    api = [int](((& $adb -s $Serial shell getprop ro.build.version.sdk) -join '').Trim())
    android = ((& $adb -s $Serial shell getprop ro.build.version.release) -join '').Trim()
    build = ((& $adb -s $Serial shell getprop ro.build.fingerprint) -join '').Trim()
    evidenceClass = "emulator-only; not physical-device or Gboard-performance evidence"
}
$configuration | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $reportRoot 'device.json')

if (-not $SkipBuild) {
    $buildTasks = @(
        ':app:assembleDebug',
        ':app:assembleDebugAndroidTest',
        ':benchmark-host:assembleBenchmark'
    )
    if ($configuration.api -eq 36) {
        $buildTasks += ':app:assembleBenchmark'
        $buildTasks += ':autofill-test-provider:assembleDebug'
    }
    & $gradle @buildTasks '--console=plain' 2>&1 |
        Tee-Object -FilePath (Join-Path $reportRoot 'build.log') | Write-Host
    if ($LASTEXITCODE -ne 0) { throw "Emulator connected-test APK build failed." }
} else {
    'Build reused from matrix preflight.' |
        Set-Content -Encoding utf8 (Join-Path $reportRoot 'build.log')
}

$originalBackgroundStarts = ((& $adb -s $Serial shell settings get global background_activity_starts_enabled) -join '').Trim()
$targetPackage = 'com.terasumi.sellerkeyboard'
$testPackage = 'com.terasumi.sellerkeyboard.test'
function Get-RecordAudioPermissionGranted([string]$PackageName) {
    $packageDump = (& $adb -s $Serial shell dumpsys package $PackageName 2>&1) -join "`n"
    $permissionMatch = [regex]::Match(
        $packageDump,
        'android\.permission\.RECORD_AUDIO:\s+granted=(true|false)'
    )
    if (-not $permissionMatch.Success) {
        # Android 8 omits ungranted dangerous permissions from the runtime
        # permission list instead of emitting `granted=false`.
        $requestedWithoutGrant = [regex]::IsMatch(
            $packageDump,
            '(?m)^\s+android\.permission\.RECORD_AUDIO\s*$'
        ) -and $packageDump -match 'runtime permissions:'
        if ($requestedWithoutGrant) { return $false }
        throw "Unable to read RECORD_AUDIO grant state for $PackageName on $Serial."
    }
    return $permissionMatch.Groups[1].Value -eq 'true'
}
function Get-InitialRecordAudioPermissionGranted([string]$PackageName) {
    $installed = ((
        & $adb -s $Serial shell pm path $PackageName 2>$null
    ) -join '').Trim() -match '^package:'
    if (-not $installed) { return $false }
    return Get-RecordAudioPermissionGranted -PackageName $PackageName
}
$originalTargetRecordAudioPermissionGranted =
    Get-InitialRecordAudioPermissionGranted -PackageName $targetPackage
$originalTestRecordAudioPermissionGranted =
    Get-InitialRecordAudioPermissionGranted -PackageName $testPackage
$supportsDeviceConfig = $configuration.api -ge 29
$originalDeviceConfig = if ($supportsDeviceConfig) {
    ((& $adb -s $Serial shell device_config get activity_manager default_background_activity_starts_enabled) -join '').Trim()
} else {
    'unsupported'
}
try {
    & $adb -s $Serial shell settings put global background_activity_starts_enabled 1 | Out-Null
    if ($supportsDeviceConfig) {
        & $adb -s $Serial shell device_config put activity_manager default_background_activity_starts_enabled true | Out-Null
    }
    & $adb -s $Serial install -r (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk')
    if ($LASTEXITCODE -ne 0) { throw "Debug APK install failed on $Serial." }
    & $adb -s $Serial install -r (Join-Path $root 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
    if ($LASTEXITCODE -ne 0) { throw "Test APK install failed on $Serial." }
    & $adb -s $Serial install -r (Join-Path $root `
        'benchmark-host\build\outputs\apk\benchmark\benchmark-host-benchmark.apk')
    if ($LASTEXITCODE -ne 0) { throw "Benchmark host APK install failed on $Serial." }
    foreach ($packageName in @($targetPackage, $testPackage)) {
        & $adb -s $Serial shell pm grant $packageName android.permission.RECORD_AUDIO |
            Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to grant RECORD_AUDIO to $packageName on $Serial."
        }
    }

    $runner = @(& $adb -s $Serial shell am instrument -w -r `
        'com.terasumi.sellerkeyboard.test/androidx.test.runner.AndroidJUnitRunner' 2>&1)
    $runnerExitCode = $LASTEXITCODE
    $runner | Set-Content -Encoding utf8 (Join-Path $reportRoot 'instrumentation.log')
    $text = $runner -join "`n"
    $optionalSkips = ([regex]::Matches($text, 'INSTRUMENTATION_STATUS_CODE: -4')).Count
    $durationMatch = [regex]::Match($text, 'Time:\s+([0-9.]+)')
    $durationSeconds = if ($durationMatch.Success) {
        [double]::Parse($durationMatch.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
    } else {
        $null
    }
    if ($runnerExitCode -ne 0 -or $text -notmatch 'OK \(22 tests\)' -or
        $text -match 'FAILURES!!!' -or $optionalSkips -ne 2 -or
        $text -notmatch 'ImeBenchmarkActivityInstrumentedTest') {
        throw "Emulator runner did not produce 22 passing/identified results."
    }
    # Replacing the selected IME APK can leave InputMethodManagerService with a
    # selected ID but no live method binder. Resetting once after instrumentation
    # makes the subsequent real-editor probes deterministic on cold emulators.
    if ($configuration.api -ge 30) {
        & $adb -s $Serial shell ime reset | Out-Null
    }
    & $adb -s $Serial shell ime enable 'com.terasumi.sellerkeyboard/.service.SellerKeyboard' |
        Out-Null
    & $adb -s $Serial shell ime set 'com.terasumi.sellerkeyboard/.service.SellerKeyboard' |
        Out-Null
    Start-Sleep -Milliseconds 500
    $sensitiveTransitionRoot = Join-Path $reportRoot 'ime-sensitive-transition'
    & (Join-Path $PSScriptRoot 'verify-ime-sensitive-transition.ps1') `
        -Serial $Serial -OutputDirectory $sensitiveTransitionRoot
    $clipboardRoot = Join-Path $reportRoot 'clipboard-lifecycle'
    & (Join-Path $PSScriptRoot 'verify-clipboard-lifecycle.ps1') `
        -Serial $Serial -OutputDirectory $clipboardRoot
    $clipboard = Get-Content -Raw -LiteralPath (Join-Path $clipboardRoot 'summary.json') |
        ConvertFrom-Json
    if (($clipboard.networkAccountingSupported -and
            ([long]$clipboard.uidRxDelta -ne 0 -or [long]$clipboard.uidTxDelta -ne 0)) -or
        [int]$clipboard.controlledTextOccurrencesInUidLog -ne 0) {
        throw "Clipboard UID attribution failed. See $clipboardRoot"
    }
    $richContentRoot = Join-Path $reportRoot 'rich-content-targets'
    & (Join-Path $PSScriptRoot 'verify-rich-content-targets.ps1') `
        -Serial $Serial -OutputDirectory $richContentRoot
    $richContent = Get-Content -Raw -LiteralPath (Join-Path $richContentRoot 'summary.json') |
        ConvertFrom-Json
    if (($richContent.networkAccountingSupported -and
            ([long]$richContent.uidRxDelta -ne 0 -or [long]$richContent.uidTxDelta -ne 0)) -or
        [int]$richContent.controlledTextOccurrencesInUidLog -ne 0) {
        throw "Rich-content UID attribution failed. See $richContentRoot"
    }
    $remoteRichContent = $null
    if ($configuration.api -eq 36) {
        $remoteRichContentRoot = Join-Path $reportRoot 'remote-rich-content'
        & (Join-Path $PSScriptRoot 'verify-remote-rich-content-attribution.ps1') `
            -Serial $Serial -OutputDirectory $remoteRichContentRoot
        $remoteRichContent = Get-Content -Raw -LiteralPath `
            (Join-Path $remoteRichContentRoot 'summary.json') | ConvertFrom-Json
        if ($remoteRichContent.overall -ne 'PASS' -or
            [int]$remoteRichContent.acceptRequests -ne 1 -or
            [int]$remoteRichContent.rejectRequests -ne 1 -or
            [int]$remoteRichContent.unsupportedRequests -ne 0 -or
            [int]$remoteRichContent.controlledTextOccurrencesInUidLog -ne 0) {
            throw "Remote rich-content attribution failed. See $remoteRichContentRoot"
        }
    }
    $crossAppProfilesRoot = Join-Path $reportRoot 'cross-app-profiles'
    & (Join-Path $PSScriptRoot 'verify-cross-app-profiles.ps1') `
        -Serial $Serial -OutputDirectory $crossAppProfilesRoot
    $sellerWorkflowVerified = $false
    $sellerWorkflowAttribution = $null
    $voiceTranslation = $null
    $voicePermission = $null
    $realProviderAudio = $null
    $translationUi = $null
    $coreInteractions = $null
    $inlineAutofillPlatform = $null
    if ($configuration.api -eq 36) {
        $inlineAutofillRoot = Join-Path $reportRoot 'inline-autofill-platform'
        & (Join-Path $PSScriptRoot 'verify-inline-autofill-platform.ps1') `
            -Serial $Serial -OutputDirectory $inlineAutofillRoot
        $inlineAutofillPlatform = Get-Content -Raw -LiteralPath `
            (Join-Path $inlineAutofillRoot 'summary.json') | ConvertFrom-Json
        if ($inlineAutofillPlatform.overall -ne 'PASS' -or
            -not $inlineAutofillPlatform.inlineRequestPresent -or
            -not $inlineAutofillPlatform.datasetReturned -or
            $inlineAutofillPlatform.imeCandidateSourceBefore -ne 'INLINE_AUTOFILL' -or
            [int]$inlineAutofillPlatform.imeInlineCountBefore -ne 1 -or
            [int]$inlineAutofillPlatform.imeInlineCountAfter -ne 0 -or
            [int]$inlineAutofillPlatform.usernameLength -ne 31 -or
            [int]$inlineAutofillPlatform.passwordLength -ne 26 -or
            -not $inlineAutofillPlatform.restoration.autofillServiceExact -or
            -not $inlineAutofillPlatform.restoration.defaultImeExact -or
            -not $inlineAutofillPlatform.restoration.providerPresenceExact) {
            throw "Platform inline-autofill evidence failed. See $inlineAutofillRoot"
        }
        $sellerWorkflowRoot = Join-Path $reportRoot 'seller-workflow'
        & (Join-Path $PSScriptRoot 'verify-connected-seller-workflow.ps1') `
            -Serial $Serial -OutputDirectory $sellerWorkflowRoot
        $sellerWorkflowAttribution = Get-Content -Raw -LiteralPath `
            (Join-Path $sellerWorkflowRoot 'summary.json') | ConvertFrom-Json
        if ($sellerWorkflowAttribution.overall -ne 'PASS' -or
            -not $sellerWorkflowAttribution.favoriteSmartOrderingVerified -or
            -not $sellerWorkflowAttribution.favoriteToggleVerified -or
            -not $sellerWorkflowAttribution.categoryOrderingVerified -or
            -not $sellerWorkflowAttribution.recentOrderingVerified -or
            -not $sellerWorkflowAttribution.slashShortcutFilteringVerified -or
            ($sellerWorkflowAttribution.networkAccountingSupported -and
                ([long]$sellerWorkflowAttribution.uidRxDelta -ne 0 -or
                    [long]$sellerWorkflowAttribution.uidTxDelta -ne 0)) -or
            [int]$sellerWorkflowAttribution.controlledTextOccurrencesInUidLog -ne 0) {
            throw "Seller workflow UID attribution failed. See $sellerWorkflowRoot"
        }
        $sellerWorkflowVerified = $true
        $coreInteractionsRoot = Join-Path $reportRoot 'core-interactions'
        & (Join-Path $PSScriptRoot 'verify-connected-core-interactions.ps1') `
            -Serial $Serial -OutputDirectory $coreInteractionsRoot
        $coreInteractions = Get-Content -Raw -LiteralPath `
            (Join-Path $coreInteractionsRoot 'summary.json') | ConvertFrom-Json
        if ($coreInteractions.overall -ne 'PASS' -or
            -not $coreInteractions.oneShotShiftVerified -or
            -not $coreInteractions.capsLockDoubleTapVerified -or
            -not $coreInteractions.punctuationAutoCapsVerified -or
            -not $coreInteractions.spaceCursorMoveVerified -or
            -not $coreInteractions.spacePointerExitRestoreVerified -or
            -not $coreInteractions.swipeDeleteVerified -or
            -not $coreInteractions.swipeDeletePointerExitRestoreVerified -or
            -not $coreInteractions.holdDeleteRepeatVerified -or
            -not $coreInteractions.holdDeleteStopsOnReleaseVerified) {
            throw "Connected core interaction gate failed. See $coreInteractionsRoot"
        }
        $voiceTranslationRoot = Join-Path $reportRoot 'voice-translation-attribution'
        & (Join-Path $PSScriptRoot 'verify-voice-translation-attribution.ps1') `
            -Serial $Serial -OutputDirectory $voiceTranslationRoot
        $voiceTranslation = Get-Content -Raw -LiteralPath `
            (Join-Path $voiceTranslationRoot 'summary.json') | ConvertFrom-Json
        $voicePermissionRoot = Join-Path $reportRoot 'voice-permission'
        & (Join-Path $PSScriptRoot 'verify-voice-permission-disclosure.ps1') `
            -Serial $Serial -OutputDirectory $voicePermissionRoot -SkipBuild -SkipInstall
        $voicePermission = Get-Content -Raw -LiteralPath `
            (Join-Path $voicePermissionRoot 'summary.json') | ConvertFrom-Json
        if ($voicePermission.overall -ne 'PASS' -or
            -not [bool]$voicePermission.restoration.recordAudioPermissionExact -or
            -not [bool]$voicePermission.restoration.defaultImeExact) {
            throw "Voice permission/disclosure gate failed. See $voicePermissionRoot"
        }
        $realProviderAudioRoot = Join-Path $reportRoot 'real-provider-audio'
        & (Join-Path $PSScriptRoot 'verify-real-provider-audio.ps1') `
            -Serial $Serial -OutputDirectory $realProviderAudioRoot -SkipBuild -SkipInstall
        $realProviderAudio = Get-Content -Raw -LiteralPath `
            (Join-Path $realProviderAudioRoot 'summary.json') | ConvertFrom-Json
        if ($realProviderAudio.overall -ne 'PASS' -or
            $realProviderAudio.state -ne 'Result' -or
            -not [bool]$realProviderAudio.audio.transferredByteExact -or
            -not [bool]$realProviderAudio.editorTextHashUnchanged -or
            -not [bool]$realProviderAudio.restoration.privateAudioFixtureDeleted) {
            throw "Configured-provider controlled-audio gate failed. See $realProviderAudioRoot"
        }
        $translationUiRoot = Join-Path $reportRoot 'translation-ui'
        & (Join-Path $PSScriptRoot 'verify-connected-translation-ui.ps1') `
            -Serial $Serial -OutputDirectory $translationUiRoot
        $translationUi = Get-Content -Raw -LiteralPath `
            (Join-Path $translationUiRoot 'summary.json') | ConvertFrom-Json
        if ($translationUi.overall -ne 'PASS' -or
            -not $translationUi.previewVerified -or
            -not $translationUi.replacedSelection -or
            -not $translationUi.restoredOriginal -or
            -not $translationUi.insertedAfterOriginalOnNewLine -or
            [int]$translationUi.imeAttribution.canaryOccurrencesInUidLog -ne 0 -or
            [int]$translationUi.providerAttribution.canaryOccurrencesInUidLog -ne 0) {
            throw "Connected EN-to-VI translation UI failed. See $translationUiRoot"
        }
    }
    $privacyRoot = Join-Path $reportRoot 'privacy-network-probe'
    & (Join-Path $PSScriptRoot 'capture-privacy-network-evidence.ps1') `
        -Serial $Serial -OutputDirectory $privacyRoot
    $privacySummaryPath = Join-Path $privacyRoot 'summary.json'
    if (-not (Test-Path -LiteralPath $privacySummaryPath)) {
        throw "Emulator privacy probe summary is missing."
    }
    $privacy = Get-Content -Raw -LiteralPath $privacySummaryPath | ConvertFrom-Json
    $unverifiedPrivacyCases = @($privacy.cases | Where-Object { -not $_.editorModeVerified })
    if ([int]$privacy.uid -lt 10000 -or
        ($privacy.networkAccountingSupported -and
            ([long]$privacy.rxDelta -ne 0 -or [long]$privacy.txDelta -ne 0)) -or
        [int]$privacy.canaryOccurrencesInUidLog -ne 0 -or
        $unverifiedPrivacyCases.Count -ne 0) {
        throw "Emulator privacy/network probe failed. See $privacySummaryPath"
    }
    $syncReleaseBoundary = $null
    if ($configuration.api -eq 36) {
        $syncReleaseRoot = Join-Path $reportRoot 'sync-release-boundary'
        & (Join-Path $PSScriptRoot 'verify-sync-release-boundary.ps1') `
            -Serial $Serial -OutputDirectory $syncReleaseRoot
        $syncReleaseBoundary = Get-Content -Raw -LiteralPath `
            (Join-Path $syncReleaseRoot 'summary.json') | ConvertFrom-Json
        if ($syncReleaseBoundary.overall -ne 'PASS' -or
            $syncReleaseBoundary.clientInitialized -or
            $syncReleaseBoundary.authInitialized -or
            $syncReleaseBoundary.backupInitialized -or
            [long]$syncReleaseBoundary.uidRxDelta -ne 0 -or
            [long]$syncReleaseBoundary.uidTxDelta -ne 0 -or
            [int]$syncReleaseBoundary.controlledTextOccurrencesInUidLog -ne 0) {
            throw "Sync release-boundary attribution failed. See $syncReleaseRoot"
        }
    }
    $releaseTranslation = $null
    if ($configuration.api -eq 36) {
        $releaseTranslationRoot = Join-Path $reportRoot 'translation-release-attribution'
        & (Join-Path $PSScriptRoot 'verify-translation-release-attribution.ps1') `
            -Serial $Serial -OutputDirectory $releaseTranslationRoot
        $releaseTranslation = Get-Content -Raw -LiteralPath `
            (Join-Path $releaseTranslationRoot 'summary.json') | ConvertFrom-Json
    }
    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        api = $configuration.api
        results = 22
        optionalBackendSkips = 2
        durationSeconds = $durationSeconds
        sensitiveTransitionVerified = $true
        clipboardLifecycleVerified = $true
        clipboardNetworkAccountingSupported = [bool]$clipboard.networkAccountingSupported
        clipboardUidRxDelta = $clipboard.uidRxDelta
        clipboardUidTxDelta = $clipboard.uidTxDelta
        clipboardControlledTextOccurrencesInUidLog = `
            [int]$clipboard.controlledTextOccurrencesInUidLog
        richContentTargetCases = 3
        richContentNetworkAccountingSupported = [bool]$richContent.networkAccountingSupported
        richContentUidRxDelta = $richContent.uidRxDelta
        richContentUidTxDelta = $richContent.uidTxDelta
        richContentControlledTextOccurrencesInUidLog = `
            [int]$richContent.controlledTextOccurrencesInUidLog
        remoteRichContentVerified = [bool]($remoteRichContent -and
            $remoteRichContent.overall -eq 'PASS')
        remoteRichContentAcceptRequests = if ($remoteRichContent) {
            [int]$remoteRichContent.acceptRequests
        } else { $null }
        remoteRichContentRejectRequests = if ($remoteRichContent) {
            [int]$remoteRichContent.rejectRequests
        } else { $null }
        remoteRichContentUnsupportedRequests = if ($remoteRichContent) {
            [int]$remoteRichContent.unsupportedRequests
        } else { $null }
        remoteRichContentControlledTextOccurrencesInUidLog = if ($remoteRichContent) {
            [int]$remoteRichContent.controlledTextOccurrencesInUidLog
        } else { $null }
        crossAppProfilesVerified = $true
        inlineAutofillPlatformVerified = [bool]($inlineAutofillPlatform -and
            $inlineAutofillPlatform.overall -eq 'PASS')
        inlineAutofillRequestCount = if ($inlineAutofillPlatform) {
            [int]$inlineAutofillPlatform.providerRequestCount
        } else { $null }
        inlineAutofillImeRxDelta = if ($inlineAutofillPlatform) {
            [long]$inlineAutofillPlatform.privacy.ime.rxDelta
        } else { $null }
        inlineAutofillImeTxDelta = if ($inlineAutofillPlatform) {
            [long]$inlineAutofillPlatform.privacy.ime.txDelta
        } else { $null }
        inlineAutofillProviderRxDelta = if ($inlineAutofillPlatform) {
            [long]$inlineAutofillPlatform.privacy.provider.rxDelta
        } else { $null }
        inlineAutofillProviderTxDelta = if ($inlineAutofillPlatform) {
            [long]$inlineAutofillPlatform.privacy.provider.txDelta
        } else { $null }
        inlineAutofillControlledTextOccurrencesInLogs = if ($inlineAutofillPlatform) {
            [int]$inlineAutofillPlatform.privacy.ime.canaryOccurrencesInUidLog +
                [int]$inlineAutofillPlatform.privacy.provider.canaryOccurrencesInUidLog +
                [int]$inlineAutofillPlatform.privacy.host.canaryOccurrencesInUidLog
        } else { $null }
        coreInteractionsVerified = [bool]($coreInteractions -and
            $coreInteractions.overall -eq 'PASS')
        oneShotShiftVerified = [bool]($coreInteractions -and
            $coreInteractions.oneShotShiftVerified)
        capsLockDoubleTapVerified = [bool]($coreInteractions -and
            $coreInteractions.capsLockDoubleTapVerified)
        punctuationAutoCapsVerified = [bool]($coreInteractions -and
            $coreInteractions.punctuationAutoCapsVerified)
        spaceCursorMoveVerified = [bool]($coreInteractions -and
            $coreInteractions.spaceCursorMoveVerified)
        spacePointerExitRestoreVerified = [bool]($coreInteractions -and
            $coreInteractions.spacePointerExitRestoreVerified)
        swipeDeleteVerified = [bool]($coreInteractions -and
            $coreInteractions.swipeDeleteVerified)
        swipeDeletePointerExitRestoreVerified = [bool]($coreInteractions -and
            $coreInteractions.swipeDeletePointerExitRestoreVerified)
        holdDeleteRepeatVerified = [bool]($coreInteractions -and
            $coreInteractions.holdDeleteRepeatVerified)
        holdDeleteStopsOnReleaseVerified = [bool]($coreInteractions -and
            $coreInteractions.holdDeleteStopsOnReleaseVerified)
        multiPointerCancellationVerified = $true
        inputViewTeardownCancellationVerified = $true
        sellerWorkflowVerified = $sellerWorkflowVerified
        sellerFavoriteSmartOrderingVerified = if ($sellerWorkflowAttribution) {
            [bool]$sellerWorkflowAttribution.favoriteSmartOrderingVerified
        } else { $false }
        sellerFavoriteToggleVerified = if ($sellerWorkflowAttribution) {
            [bool]$sellerWorkflowAttribution.favoriteToggleVerified
        } else { $false }
        sellerCategoryOrderingVerified = if ($sellerWorkflowAttribution) {
            [bool]$sellerWorkflowAttribution.categoryOrderingVerified
        } else { $false }
        sellerRecentOrderingVerified = if ($sellerWorkflowAttribution) {
            [bool]$sellerWorkflowAttribution.recentOrderingVerified
        } else { $false }
        sellerSlashShortcutFilteringVerified = if ($sellerWorkflowAttribution) {
            [bool]$sellerWorkflowAttribution.slashShortcutFilteringVerified
        } else { $false }
        sellerWorkflowNetworkAccountingSupported = if ($sellerWorkflowAttribution) {
            [bool]$sellerWorkflowAttribution.networkAccountingSupported
        } else { $null }
        sellerWorkflowUidRxDelta = if ($sellerWorkflowAttribution) {
            $sellerWorkflowAttribution.uidRxDelta
        } else { $null }
        sellerWorkflowUidTxDelta = if ($sellerWorkflowAttribution) {
            $sellerWorkflowAttribution.uidTxDelta
        } else { $null }
        sellerWorkflowControlledTextOccurrencesInUidLog = if ($sellerWorkflowAttribution) {
            [int]$sellerWorkflowAttribution.controlledTextOccurrencesInUidLog
        } else { $null }
        voiceTranslationAttributionVerified = [bool]($voiceTranslation -and
            $voiceTranslation.overall -eq 'PASS')
        voiceProviderPackage = if ($voiceTranslation) {
            $voiceTranslation.voice.providerPackage
        } else { $null }
        voiceImeUidRxDelta = if ($voiceTranslation) {
            $voiceTranslation.voice.imeAttribution.rxDelta
        } else { $null }
        voiceImeUidTxDelta = if ($voiceTranslation) {
            $voiceTranslation.voice.imeAttribution.txDelta
        } else { $null }
        voicePermissionDisclosureVerified = [bool]($voicePermission -and
            $voicePermission.overall -eq 'PASS')
        voicePermissionStateRestoredExactly = [bool]($voicePermission -and
            $voicePermission.restoration.recordAudioPermissionExact -and
            $voicePermission.restoration.defaultImeExact)
        realProviderControlledAudioVerified = [bool]($realProviderAudio -and
            $realProviderAudio.overall -eq 'PASS')
        realProviderControlledAudioTranscriptSha256 = if ($realProviderAudio) {
            $realProviderAudio.transcriptSha256
        } else { $null }
        realProviderControlledAudioEditorUnchanged = [bool]($realProviderAudio -and
            $realProviderAudio.editorTextHashUnchanged)
        translationPreviewVerified = [bool]($voiceTranslation -and
            $voiceTranslation.translation.finalState -eq 'Preview')
        translationControlledTextOccurrencesInAttributedLogs = if ($voiceTranslation) {
            [int]$voiceTranslation.translation.imeAttribution.canaryOccurrencesInUidLog +
                [int]$voiceTranslation.translation.providerAttribution.canaryOccurrencesInUidLog
        } else { $null }
        translationUiVerified = [bool]($translationUi -and $translationUi.overall -eq 'PASS')
        translationEnToViPreviewVerified = [bool]($translationUi -and
            $translationUi.previewVerified)
        translationSelectionReplaceVerified = [bool]($translationUi -and
            $translationUi.replacedSelection)
        translationRestoreOriginalVerified = [bool]($translationUi -and
            $translationUi.restoredOriginal)
        translationInsertVerified = [bool]($translationUi -and
            $translationUi.insertedAfterOriginalOnNewLine)
        translationUiControlledTextOccurrencesInAttributedLogs = if ($translationUi) {
            [int]$translationUi.imeAttribution.canaryOccurrencesInUidLog +
                [int]$translationUi.providerAttribution.canaryOccurrencesInUidLog
        } else { $null }
        releaseLikeTranslationAttributionVerified = [bool]($releaseTranslation -and
            $releaseTranslation.overall -eq 'PASS')
        releaseLikeTranslationControlledTextOccurrencesInAttributedLogs = if ($releaseTranslation) {
            [int]$releaseTranslation.imeAttribution.canaryOccurrencesInUidLog +
                [int]$releaseTranslation.providerAttribution.canaryOccurrencesInUidLog
        } else { $null }
        syncDeferredReleaseBoundaryVerified = [bool]($syncReleaseBoundary -and
            $syncReleaseBoundary.overall -eq 'PASS')
        syncCloudDependenciesInitialized = if ($syncReleaseBoundary) {
            [bool]($syncReleaseBoundary.clientInitialized -or
                $syncReleaseBoundary.authInitialized -or
                $syncReleaseBoundary.backupInitialized)
        } else { $null }
        syncUidRxDelta = if ($syncReleaseBoundary) {
            $syncReleaseBoundary.uidRxDelta
        } else { $null }
        syncUidTxDelta = if ($syncReleaseBoundary) {
            $syncReleaseBoundary.uidTxDelta
        } else { $null }
        syncControlledTextOccurrencesInUidLog = if ($syncReleaseBoundary) {
            [int]$syncReleaseBoundary.controlledTextOccurrencesInUidLog
        } else { $null }
        privacyCases = @($privacy.cases | ForEach-Object { $_.id })
        privacyNetworkAccountingSupported = [bool]$privacy.networkAccountingSupported
        privacyRxDelta = $privacy.rxDelta
        privacyTxDelta = $privacy.txDelta
        privacyCanaryOccurrencesInUidLog = [int]$privacy.canaryOccurrencesInUidLog
        claimBoundary = $configuration.evidenceClass
    } | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $reportRoot 'summary.json')
} finally {
    if ($originalBackgroundStarts -eq 'null' -or [string]::IsNullOrWhiteSpace($originalBackgroundStarts)) {
        & $adb -s $Serial shell settings delete global background_activity_starts_enabled | Out-Null
    } else {
        & $adb -s $Serial shell settings put global background_activity_starts_enabled $originalBackgroundStarts | Out-Null
    }
    if ($supportsDeviceConfig) {
        if ($originalDeviceConfig -eq 'null' -or [string]::IsNullOrWhiteSpace($originalDeviceConfig)) {
            & $adb -s $Serial shell device_config delete activity_manager default_background_activity_starts_enabled | Out-Null
        } else {
            & $adb -s $Serial shell device_config put activity_manager default_background_activity_starts_enabled $originalDeviceConfig | Out-Null
        }
    }
    foreach ($permissionState in @(
        [pscustomobject]@{
            packageName = $targetPackage
            originallyGranted = $originalTargetRecordAudioPermissionGranted
        },
        [pscustomobject]@{
            packageName = $testPackage
            originallyGranted = $originalTestRecordAudioPermissionGranted
        }
    )) {
        if ($permissionState.originallyGranted) {
            & $adb -s $Serial shell pm grant $permissionState.packageName `
                android.permission.RECORD_AUDIO | Out-Null
        } else {
            & $adb -s $Serial shell pm revoke $permissionState.packageName `
                android.permission.RECORD_AUDIO | Out-Null
        }
    }
}

$recordAudioPermissionRestoredExactly = (
    (Get-RecordAudioPermissionGranted -PackageName $targetPackage) -eq
        $originalTargetRecordAudioPermissionGranted
) -and (
    (Get-RecordAudioPermissionGranted -PackageName $testPackage) -eq
        $originalTestRecordAudioPermissionGranted
)
if (-not $recordAudioPermissionRestoredExactly) {
    throw "RECORD_AUDIO permission was not restored exactly on $Serial."
}
$summaryPath = Join-Path $reportRoot 'summary.json'
$completedSummary = Get-Content -Raw -LiteralPath $summaryPath | ConvertFrom-Json
$completedSummary | Add-Member -NotePropertyName recordAudioPermissionRestoredExactly `
    -NotePropertyValue $true -Force
$completedSummary | ConvertTo-Json -Depth 8 |
    Set-Content -Encoding utf8 $summaryPath

Write-Host "Emulator connected gates passed: $reportRoot"
