[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(26, 30, 31, 34, 35, 36)]
    [int]$ApiLevel,
    [string]$DeviceProfile = "medium_phone",
    [string]$AvdName = ""
)

$ErrorActionPreference = "Stop"
$sdkRoot = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($sdkRoot)) { throw "ANDROID_HOME is not set." }
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
$avdManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\avdmanager.bat'
$emulator = Join-Path $sdkRoot 'emulator\emulator.exe'
$package = "system-images;android-$ApiLevel;google_apis;x86_64"
$imagePath = Join-Path $sdkRoot "system-images\android-$ApiLevel\google_apis\x86_64"
$resolvedAvdName = if (-not [string]::IsNullOrWhiteSpace($AvdName)) {
    $AvdName
} elseif ($ApiLevel -eq 36 -and $DeviceProfile -eq 'medium_phone') {
    'SellerKeyboardSmoke'
} else {
    "SellerKeyboardApi$ApiLevel"
}

if (-not (Test-Path -LiteralPath $imagePath)) {
    & $sdkManager $package
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $imagePath)) {
        throw "Failed to install $package."
    }
}

$avds = @(& $emulator -list-avds)
if ($resolvedAvdName -notin $avds) {
    'no' | & $avdManager create avd -n $resolvedAvdName -k $package -d $DeviceProfile --force
    if ($LASTEXITCODE -ne 0) { throw "Failed to create AVD $resolvedAvdName." }
}

& (Join-Path $PSScriptRoot 'run-emulator-connected.ps1') -AvdName $resolvedAvdName
