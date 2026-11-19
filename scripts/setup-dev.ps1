[CmdletBinding()]
param(
    [string]$SdkRoot,
    [switch]$Verify,
    [switch]$Install,
    [string]$Serial
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($Install -and [string]::IsNullOrWhiteSpace($Serial)) {
    throw 'Use -Install -Serial <adb-device-id> to select the device explicitly.'
}

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw 'Install Git for Windows, then open a new terminal.'
    }
    git config --local core.longpaths true
    if ($LASTEXITCODE -ne 0) { throw 'Could not enable long paths for this repository.' }
    $indexPath = git rev-parse --git-path index
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $indexPath)) {
        throw 'Git checkout is incomplete (missing index). See Getting Started in README.md before editing files.'
    }

    $java = if ($env:JAVA_HOME) {
        Join-Path $env:JAVA_HOME 'bin\java.exe'
    } else {
        $command = Get-Command java -ErrorAction SilentlyContinue
        if (-not $command) { throw 'Install JDK 17 and set JAVA_HOME to its installation directory.' }
        $command.Source
    }
    if (-not (Test-Path -LiteralPath $java)) {
        throw "Java executable not found: $java. Correct JAVA_HOME before continuing."
    }
    $javaVersion = (& $java --version) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the Java version. This project requires JDK 17 or newer.' }
    $javaMatch = [regex]::Match($javaVersion, '(?m)^(?:openjdk|java)\s+(\d+)')
    if (-not $javaMatch.Success -or [int]$javaMatch.Groups[1].Value -lt 17) {
        throw "Unsupported Java version. Install JDK 17 or newer.`n$javaVersion"
    }

    $propertiesPath = Join-Path $root 'local.properties'
    $properties = if (Test-Path -LiteralPath $propertiesPath) {
        @([IO.File]::ReadAllLines($propertiesPath))
    } else {
        @('# Local Android SDK location; do not commit this file.')
    }
    if ([string]::IsNullOrWhiteSpace($SdkRoot)) {
        $sdkLine = $properties | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($sdkLine) {
            $SdkRoot = ($sdkLine -split '=', 2)[1].Trim().Replace('\:', ':').Replace('\\', '\')
            $SdkRoot = [regex]::Replace($SdkRoot, '\\u([0-9a-fA-F]{4})', {
                param($match)
                [string][char][Convert]::ToInt32($match.Groups[1].Value, 16)
            })
        } else {
            $candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
            if ($env:LOCALAPPDATA) { $candidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
            if ($env:SystemDrive) { $candidates += "$($env:SystemDrive)\Android\Sdk" }
            $SdkRoot = $candidates | Where-Object {
                -not [string]::IsNullOrWhiteSpace($_) -and (Test-Path -LiteralPath $_ -PathType Container)
            } | Select-Object -First 1
        }
    }
    if ([string]::IsNullOrWhiteSpace($SdkRoot) -or -not (Test-Path -LiteralPath $SdkRoot -PathType Container)) {
        throw 'Android SDK not found. Install it with Android Studio, then pass -SdkRoot <sdk-directory>.'
    }
    $SdkRoot = (Resolve-Path -LiteralPath $SdkRoot).Path
    $required = [ordered]@{
        'platform-tools' = 'platform-tools\adb.exe'
        'platforms;android-36' = 'platforms\android-36\android.jar'
        'build-tools;36.0.0' = 'build-tools\36.0.0\aapt2.exe'
        'ndk;27.0.12077973' = 'ndk\27.0.12077973\ndk-build.cmd'
    }
    $missing = @($required.Keys | Where-Object {
        -not (Test-Path -LiteralPath (Join-Path $SdkRoot $required[$_]) -PathType Leaf)
    })
    if ($missing.Count -gt 0) {
        throw "Missing SDK packages: $($missing -join ', '). Install these exact packages in Android Studio's SDK Manager."
    }

    # Forward slashes avoid Windows backslash escapes in Java properties. Escape
    # the drive separator and non-ASCII characters for Java properties and lint.
    $sdkValue = -join ($SdkRoot.Replace('\', '/').ToCharArray() | ForEach-Object {
        if ([int]$_ -gt 127) { '\u{0:x4}' -f [int]$_ } else { [string]$_ }
    })
    $sdkValue = $sdkValue.Replace(':', '\:')
    $properties = @($properties | Where-Object { $_ -notmatch '^\s*sdk\.dir\s*=' })
    $properties += "sdk.dir=$sdkValue"
    [IO.File]::WriteAllLines($propertiesPath, [string[]]$properties, [Text.UTF8Encoding]::new($false))
    $env:ANDROID_HOME = $SdkRoot
    $env:ANDROID_SDK_ROOT = $SdkRoot
    $adb = Join-Path $SdkRoot 'platform-tools\adb.exe'

    Write-Output "Java: $($javaVersion.Split("`n")[0])"
    Write-Output "Android SDK: $SdkRoot"
    Write-Output 'Git long paths: enabled locally; local.properties: configured'
    & $adb devices -l
    if ($LASTEXITCODE -ne 0) { throw 'Could not list Android devices.' }
    if ($Install) {
        & $adb -s $Serial get-state
        if ($LASTEXITCODE -ne 0) { throw "Device '$Serial' is not connected and authorized." }
    }

    $tasks = @()
    if ($Verify -or $Install) { $tasks += ':app:assembleDebug' }
    if ($Verify) { $tasks += ':app:testDebugUnitTest' }
    if ($tasks.Count -gt 0) {
        & (Join-Path $root 'gradlew.bat') @tasks
        if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed; see the diagnostics above.' }
    }
    if ($Install) {
        & $adb -s $Serial install -r (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk')
        if ($LASTEXITCODE -ne 0) {
            throw 'Install failed. Existing app data was not removed; do not uninstall to bypass a signing conflict.'
        }
    }
    Write-Output 'Development setup is ready.'
} finally {
    Pop-Location
}
