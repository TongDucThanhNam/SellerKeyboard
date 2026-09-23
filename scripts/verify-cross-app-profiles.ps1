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
$sellerActivity = "$sellerPackage/.benchmark.ImeBenchmarkActivity"
$hostActivity = "$hostPackage/.ImeBenchmarkActivity"
$bridge = "content://$sellerPackage.ime-test-bridge"
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

function Capture-Window([string]$Name) {
    $remote = "/sdcard/seller-profile-$Name.xml"
    $local = Join-Path $OutputDirectory "$Name-window.xml"
    Adb '-s' $Serial shell uiautomator dump $remote | Out-Null
    Adb '-s' $Serial pull '-q' $remote $local | Out-Null
    $local
}

function Invoke-BoundsTap([System.Text.RegularExpressions.Match]$Bounds) {
    $x = [int](([int]$Bounds.Groups[1].Value + [int]$Bounds.Groups[3].Value) / 2)
    $y = [int](([int]$Bounds.Groups[2].Value + [int]$Bounds.Groups[4].Value) / 2)
    Adb '-s' $Serial shell input tap $x $y | Out-Null
}

function Focus-Editor([string]$Hint, [string]$Name) {
    $hintPattern = [regex]::Escape($Hint)
    foreach ($attempt in 1..5) {
        $local = Capture-Window "$Name-focus"
        $xml = Get-Content -Raw -Encoding UTF8 -LiteralPath $local
        $node = [regex]::Match($xml, '<node[^>]*(?:hint|text)="' + $hintPattern + '"[^>]*/?>')
        if ($node.Success) {
            $bounds = [regex]::Match($node.Value, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
            if (-not $bounds.Success) { throw "Profile editor has no bounds in $local" }
            Invoke-BoundsTap $bounds
            Start-Sleep -Milliseconds 500
            return
        }
        $waitNode = [regex]::Match($xml, '<node[^>]*text="Wait"[^>]*/?>')
        if ($waitNode.Success) {
            $waitBounds = [regex]::Match(
                $waitNode.Value,
                'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
            )
            if ($waitBounds.Success) { Invoke-BoundsTap $waitBounds }
        }
        Start-Sleep -Seconds 1
    }
    throw "Could not locate '$Hint' in $local"
}

function Start-SellerEditor {
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $sellerActivity '--es' 'hintText' 'ProfileSellerEditor' | Out-Null
    Focus-Editor 'ProfileSellerEditor' 'seller'
}

function Start-HostEditor {
    Adb '-s' $Serial shell am start '-W' '--activity-clear-top' '--activity-single-top' `
        '-n' $hostActivity | Out-Null
    Focus-Editor 'IME benchmark editor' 'host'
}

function Await-Profile(
    [string]$PackageName,
    [string]$ProfileId,
    [long]$AfterSessionId,
    [hashtable]$Expected
) {
    $deadline = (Get-Date).AddSeconds(12)
    do {
        $snapshot = Call-Bridge 'snapshot'
        $sessionId = [long](Bundle-Value $snapshot 'session_id')
        if ((Bundle-Value $snapshot 'active') -eq 'true' -and
            $sessionId -gt $AfterSessionId -and
            (Bundle-Value $snapshot 'editor_package') -eq $PackageName -and
            (Bundle-Value $snapshot 'active_profile_id') -eq $ProfileId) {
            foreach ($key in $Expected.Keys) {
                $actual = Bundle-Value $snapshot $key
                if ($actual -ne [string]$Expected[$key]) {
                    throw "Profile '$ProfileId' expected $key=$($Expected[$key]), got '$actual': $snapshot"
                }
            }
            return $snapshot
        }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $PackageName/$ProfileId after session $AfterSessionId. Last: $snapshot"
}

$originalShowImeWithHardKeyboard = ((Adb '-s' $Serial shell settings get secure `
    show_ime_with_hard_keyboard) -join '').Trim()
$seeded = $false
try {
    Adb '-s' $Serial shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    Adb '-s' $Serial shell ime enable $ime | Out-Null
    Adb '-s' $Serial shell ime set $ime | Out-Null

    Start-SellerEditor
    $initial = Call-Bridge 'snapshot'
    $seedResult = Call-Bridge 'seed_app_profiles'
    if ((Bundle-Value $seedResult 'app_profiles_seeded') -ne 'true') {
        throw "Debug profile seed failed: $seedResult"
    }
    $seeded = $true

    $lastSessionId = [long](Bundle-Value $initial 'session_id')
    Start-SellerEditor
    $seller = Await-Profile $sellerPackage 'connected_seller_profile' $lastSessionId @{
        active_profile_name = 'Connected Seller'
        preferred_reply_pack_id = 'connected-seller-pack'
        language_mode = 'VIETNAMESE'
        number_row = 'true'
        height_preset = 'TALL'
        theme_mode = 'LIGHT'
        toolbar_order = 'replies|calculator'
    }

    $lastSessionId = [long](Bundle-Value $seller 'session_id')
    Start-HostEditor
    $hostSnapshot = Await-Profile $hostPackage 'connected_host_profile' $lastSessionId @{
        active_profile_name = 'Connected Host'
        preferred_reply_pack_id = 'connected-host-pack'
        language_mode = 'ENGLISH'
        number_row = 'false'
        height_preset = 'COMPACT'
        theme_mode = 'DARK'
        toolbar_order = 'products|clipboard'
    }

    $unbind = Call-Bridge 'unbind_host_profile'
    if ((Bundle-Value $unbind 'host_profile_unbound') -ne 'true') {
        throw "Host profile unbind failed: $unbind"
    }
    $lastSessionId = [long](Bundle-Value $hostSnapshot 'session_id')
    Start-HostEditor
    $global = Await-Profile $hostPackage 'global' $lastSessionId @{
        active_profile_name = 'Global'
        language_mode = 'VIETNAMESE_ENGLISH'
        number_row = 'true'
        height_preset = 'STANDARD'
        theme_mode = 'SYSTEM'
        toolbar_order = 'replies|products|clipboard|calculator|translate|voice|emoji|settings'
    }

    [ordered]@{
        generatedAt = (Get-Date).ToString('o')
        overall = 'PASS'
        serial = $Serial
        sellerPackage = $sellerPackage
        hostPackage = $hostPackage
        distinctPackages = $true
        activeEditorPackageOnly = $true
        globalFallbackVerified = $true
        sellerProfile = $seller
        hostProfile = $hostSnapshot
        globalFallback = $global
        packageInventoryPermissionUsed = $false
        claimBoundary = 'real cross-app IME sessions on the emulator; named third-party apps remain pending'
    } | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 `
        (Join-Path $OutputDirectory 'summary.json')
    Write-Host "Cross-app profile switching passed: $OutputDirectory"
} finally {
    if ($seeded) {
        $restore = Call-Bridge 'restore_app_profiles'
        if ((Bundle-Value $restore 'app_profiles_restored') -ne 'true') {
            Write-Warning "Debug profile restore did not confirm success: $restore"
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
