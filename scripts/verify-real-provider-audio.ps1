[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [switch]$SkipBuild,
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$OutputDirectory = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
    [IO.Path]::GetFullPath($OutputDirectory)
} else {
    [IO.Path]::GetFullPath((Join-Path (Get-Location).Path $OutputDirectory))
}
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
} else { 'adb' }
$package = 'com.terasumi.sellerkeyboard'
$hostPackage = 'com.terasumi.sellerkeyboard.benchmarkhost'
$ime = "$package/.service.SellerKeyboard"
$activity = "$hostPackage/.ImeBenchmarkActivity"
$imeBridge = "content://$package.ime-test-bridge"
$providerAudioUri = "$imeBridge/provider-audio"
$hostBridge = "content://$hostPackage.host-test-bridge"
$phrase = 'hello world hello world'
$tokens = @('hello', 'world')
$fixtureName = 'voice-provider-fixture.pcm'
$wavPath = Join-Path $OutputDirectory 'synthesized-source.wav'
$pcmPath = Join-Path $OutputDirectory $fixtureName
$appAttributionRoot = Join-Path $OutputDirectory 'app-uid-attribution'
$providerAttributionRoot = Join-Path $OutputDirectory 'provider-uid-attribution'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Get-Sha256Hex([string]$Path) {
    $stream = [System.IO.File]::OpenRead($Path)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [System.BitConverter]::ToString($sha256.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
        $stream.Dispose()
    }
}

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $result = @(& $adb @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if ($exitCode -ne 0) {
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

function Is-RecordAudioGranted {
    $dump = (Adb '-s' $Serial shell dumpsys package $package) -join "`n"
    $match = [regex]::Match(
        $dump,
        'android\.permission\.RECORD_AUDIO:\s+granted=(true|false)'
    )
    if (-not $match.Success) { throw 'Could not resolve RECORD_AUDIO grant state.' }
    $match.Groups[1].Value -eq 'true'
}

function New-SpeechFixture {
    Add-Type -AssemblyName System.Speech
    $format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
        16000,
        [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
        [System.Speech.AudioFormat.AudioChannel]::Mono
    )
    $synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
    try {
        $synth.SelectVoiceByHints(
            [System.Speech.Synthesis.VoiceGender]::Female,
            [System.Speech.Synthesis.VoiceAge]::Adult,
            0,
            [Globalization.CultureInfo]::GetCultureInfo('en-US')
        )
        $synth.Rate = 0
        $synth.SetOutputToWaveFile($wavPath, $format)
        $synth.Speak($phrase)
    } finally {
        $synth.Dispose()
    }

    $wave = [IO.File]::ReadAllBytes($wavPath)
    if ($wave.Length -lt 44 -or
        [Text.Encoding]::ASCII.GetString($wave, 0, 4) -ne 'RIFF' -or
        [Text.Encoding]::ASCII.GetString($wave, 8, 4) -ne 'WAVE') {
        throw 'Windows speech synthesis did not produce a valid RIFF/WAVE fixture.'
    }
    $offset = 12
    $audio = $null
    while ($offset + 8 -le $wave.Length) {
        $chunk = [Text.Encoding]::ASCII.GetString($wave, $offset, 4)
        $size = [BitConverter]::ToInt32($wave, $offset + 4)
        $dataOffset = $offset + 8
        if ($size -lt 0 -or $dataOffset + $size -gt $wave.Length) {
            throw "Invalid WAVE chunk '$chunk'."
        }
        if ($chunk -eq 'data') {
            $audio = New-Object byte[] $size
            [Array]::Copy($wave, $dataOffset, $audio, 0, $size)
            break
        }
        $offset = $dataOffset + $size + ($size % 2)
    }
    if ($null -eq $audio -or $audio.Length -eq 0) {
        throw 'Synthesized WAVE fixture contained no PCM data.'
    }
    $oneSecondSilence = 16000 * 2
    $padded = New-Object byte[] ($audio.Length + (2 * $oneSecondSilence))
    [Array]::Copy($audio, 0, $padded, $oneSecondSilence, $audio.Length)
    [IO.File]::WriteAllBytes($pcmPath, $padded)
}

function Wait-ImeActive([int]$TimeoutSeconds = 45) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $snapshot = Call-Bridge $imeBridge 'feature_attribution_snapshot'
        if ((Bundle-Value $snapshot 'active') -eq 'true' -and
            (Bundle-Value $snapshot 'editor_package') -eq $hostPackage -and
            (Bundle-Value $snapshot 'sensitive') -eq 'false') {
            return $snapshot
        }
        Adb '-s' $Serial shell ime enable $ime | Out-Null
        Adb '-s' $Serial shell ime set $ime | Out-Null
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Timed out activating SellerKeyboard in the neutral host. Last: $snapshot"
}

function Wait-ProviderAudioState([int]$TimeoutSeconds = 45) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $snapshot = Call-Bridge $imeBridge 'feature_attribution_snapshot'
        $state = Bundle-Value $snapshot 'provider_audio_state'
        if ($state -in @('Result', 'Error')) { return $snapshot }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for real-provider audio result. Last: $snapshot"
}

function Start-Attribution([string]$PackageName, [string]$Directory, [string]$Expectation) {
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Start -Serial $Serial -PackageName $PackageName `
        -OutputDirectory $Directory -NetworkExpectation $Expectation
}

function Finish-Attribution(
    [string]$PackageName,
    [string]$Directory,
    [string]$Expectation
) {
    & (Join-Path $PSScriptRoot 'measure-uid-attribution.ps1') `
        -Phase Finish -Serial $Serial -PackageName $PackageName `
        -OutputDirectory $Directory -NetworkExpectation $Expectation -Canary @($phrase)
    Get-Content -Raw -LiteralPath (Join-Path $Directory 'summary.json') | ConvertFrom-Json
}

New-SpeechFixture
if (-not $SkipBuild) {
    $buildLog = Join-Path $OutputDirectory 'build.txt'
    $buildOutput = @(& (Join-Path $root 'gradlew.bat') `
        :app:assembleDebug :benchmark-host:assembleBenchmark --console=plain 2>&1)
    $buildOutput | Set-Content -Encoding utf8 -LiteralPath $buildLog
    if ($LASTEXITCODE -ne 0) { throw "Real-provider fixture build failed. See $buildLog" }
}

$appApk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
$hostApk = Join-Path $root `
    'benchmark-host\build\outputs\apk\benchmark\benchmark-host-benchmark.apk'
if (-not $SkipInstall) {
    Adb '-s' $Serial install '-r' '-t' $appApk | Out-Null
    Adb '-s' $Serial install '-r' $hostApk | Out-Null
}
$providerComponent = ((Adb '-s' $Serial shell settings get secure `
    voice_recognition_service) -join '').Trim()
if ($providerComponent -notmatch '/') {
    throw "No configured Android speech provider: $providerComponent"
}
$providerPackage = ($providerComponent -split '/', 2)[0]
$originalShowIme = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
$originalIme = ((Adb '-s' $Serial shell settings get secure `
    default_input_method) -join '').Trim()
$originalPermissionGranted = Is-RecordAudioGranted
$api = [int](((Adb '-s' $Serial shell getprop ro.build.version.sdk) -join '').Trim())
$model = ((Adb '-s' $Serial shell getprop ro.product.model) -join '').Trim()
$manufacturer = ((Adb '-s' $Serial shell getprop ro.product.manufacturer) -join '').Trim()
$fingerprint = ((Adb '-s' $Serial shell getprop ro.build.fingerprint) -join '').Trim()
$isEmulator = ((Adb '-s' $Serial shell getprop ro.kernel.qemu) -join '').Trim() -eq '1'
$summary = $null
$fixtureDeleted = $false
$fixtureBytes = (Get-Item -LiteralPath $pcmPath).Length
$fixtureSha256 = Get-Sha256Hex -Path $pcmPath

try {
    Adb '-s' $Serial push $pcmPath "/data/local/tmp/$fixtureName" | Out-Null
    Adb '-s' $Serial shell `
        "content write --uri $providerAudioUri < /data/local/tmp/$fixtureName" | Out-Null
    $deviceFixture = Call-Bridge $imeBridge 'provider_audio_fixture_snapshot'
    $deviceFixtureBytes = [long](Bundle-Value $deviceFixture 'provider_audio_fixture_bytes')
    $deviceFixtureSha256 = Bundle-Value $deviceFixture 'provider_audio_fixture_sha256'
    if ((Bundle-Value $deviceFixture 'provider_audio_fixture_exists') -ne 'true' -or
        $deviceFixtureBytes -ne $fixtureBytes -or
        $deviceFixtureSha256 -ne $fixtureSha256) {
        throw "Non-debuggable fixture transfer was not byte-exact: $deviceFixture"
    }
    Adb '-s' $Serial shell pm grant $package android.permission.RECORD_AUDIO | Out-Null
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Adb '-s' $Serial shell ime enable $ime | Out-Null
    Adb '-s' $Serial shell ime set $ime | Out-Null
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $activity '--ei' inputType 1 '--ei' imeOptions 6 `
        '--es' hintText RealProviderAudioEditor | Out-Null
    Start-Sleep -Seconds 1
    Wait-ImeActive | Out-Null
    $seeded = Call-Bridge $hostBridge 'seed_seller_benchmark_empty'
    if ((Bundle-Value $seeded 'text_length') -ne '0') {
        throw "Could not establish an empty editor baseline: $seeded"
    }
    $beforeHash = Bundle-Value $seeded 'text_sha256'

    Start-Attribution $package $appAttributionRoot Zero
    Start-Attribution $providerPackage $providerAttributionRoot Record
    Call-Bridge $imeBridge 'start_provider_audio_fixture' | Out-Null
    $result = Wait-ProviderAudioState
    $state = Bundle-Value $result 'provider_audio_state'
    $voiceError = Bundle-Value $result 'provider_audio_error'
    $transcript = Bundle-Value $result 'provider_audio_transcript'
    $normalizedTranscript = ($transcript.ToLowerInvariant() -replace '[^a-z0-9 ]', ' ' `
        -replace '\s+', ' ').Trim()
    $missingTokens = @($tokens | Where-Object {
        $normalizedTranscript -notmatch "(?:^|\s)$([regex]::Escape($_))(?:\s|$)"
    })
    if ($state -ne 'Result' -or $voiceError -or $missingTokens.Count -ne 0 -or
        (Bundle-Value $result 'provider_audio_review_required') -ne 'true') {
        throw "Real provider did not return the expected review result. " +
            "state=$state error=$voiceError transcript='$transcript' missing=$($missingTokens -join ',')"
    }

    $afterHost = Call-Bridge $hostBridge 'snapshot'
    $afterHash = Bundle-Value $afterHost 'text_sha256'
    if ((Bundle-Value $afterHost 'text_length') -ne '0' -or $afterHash -ne $beforeHash) {
        throw "Voice result changed the editor before explicit insertion: $afterHost"
    }
    $appAttribution = Finish-Attribution $package $appAttributionRoot Zero
    $providerAttribution = Finish-Attribution `
        $providerPackage $providerAttributionRoot Record

    $summary = [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        api = $api
        model = $model
        manufacturer = $manufacturer
        fingerprint = $fingerprint
        isEmulator = $isEmulator
        providerComponent = $providerComponent
        providerPackage = $providerPackage
        audio = [ordered]@{
            source = 'Windows SAPI synthesized fixture'
            phrase = $phrase
            format = 'PCM signed 16-bit little-endian, 16000 Hz, mono'
            bytes = $fixtureBytes
            sha256 = $fixtureSha256
            deviceBytes = $deviceFixtureBytes
            deviceSha256 = $deviceFixtureSha256
            transferredByteExact = $true
        }
        state = $state
        transcript = $transcript
        transcriptSha256 = Bundle-Value $result 'provider_audio_transcript_sha256'
        expectedTokens = $tokens
        missingTokens = $missingTokens
        reviewRequired = $true
        usingOnDevice = (Bundle-Value $result 'provider_audio_using_on_device') -eq 'true'
        editorTextLengthBefore = 0
        editorTextLengthAfter = [int](Bundle-Value $afterHost 'text_length')
        editorTextHashUnchanged = $afterHash -eq $beforeHash
        appAttribution = $appAttribution
        providerAttribution = $providerAttribution
        claimBoundary = 'controlled PCM through the configured real Android provider into app review state; no physical microphone/acoustic-quality claim'
    }
} finally {
    try { Call-Bridge $imeBridge 'cancel_provider_audio_fixture' | Out-Null } catch { }
    try {
        $deleteFixture = Call-Bridge $imeBridge 'delete_provider_audio_fixture'
        $fixtureDeleted = (Bundle-Value $deleteFixture 'provider_audio_fixture_deleted') -eq 'true'
    } catch { }
    try { Adb '-s' $Serial shell rm '-f' "/data/local/tmp/$fixtureName" | Out-Null } catch { }
    if ($originalPermissionGranted) {
        try { Adb '-s' $Serial shell pm grant $package android.permission.RECORD_AUDIO | Out-Null } catch { }
    } else {
        try { Adb '-s' $Serial shell pm revoke $package android.permission.RECORD_AUDIO | Out-Null } catch { }
    }
    if ($originalShowIme -eq 'null' -or [string]::IsNullOrWhiteSpace($originalShowIme)) {
        Adb '-s' $Serial shell settings delete secure show_ime_with_hard_keyboard | Out-Null
    } else {
        Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard `
            $originalShowIme | Out-Null
    }
    if (-not [string]::IsNullOrWhiteSpace($originalIme) -and $originalIme -ne 'null') {
        Adb '-s' $Serial shell ime set $originalIme | Out-Null
    }
}

$restoredPermission = (Is-RecordAudioGranted) -eq $originalPermissionGranted
$restoredShowIme = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim() -eq $originalShowIme
$restoredIme = ((Adb '-s' $Serial shell settings get secure `
    default_input_method) -join '').Trim() -eq $originalIme
if (-not $restoredPermission -or -not $restoredShowIme -or -not $restoredIme -or
    -not $fixtureDeleted) {
    throw "Real-provider verifier did not restore device state exactly: " +
        "permission=$restoredPermission showIme=$restoredShowIme " +
        "ime=$restoredIme fixtureDeleted=$fixtureDeleted"
}
$summary.restoration = [ordered]@{
    recordAudioPermissionExact = $restoredPermission
    showImeWithHardKeyboardExact = $restoredShowIme
    defaultImeExact = $restoredIme
    privateAudioFixtureDeleted = $fixtureDeleted
}
$summary | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 `
    (Join-Path $OutputDirectory 'summary.json')
Write-Host "Real-provider audio recognition passed: $OutputDirectory"
