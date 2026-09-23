[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$OutputDirectory = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
    [IO.Path]::GetFullPath($OutputDirectory)
} else {
    [IO.Path]::GetFullPath((Join-Path (Get-Location).Path $OutputDirectory))
}
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' } else { 'adb' }
$port = 18080
$imagePath = Join-Path $root 'images\logo.png'
$serverLog = Join-Path $OutputDirectory 'http-requests.txt'
$readyFile = Join-Path $OutputDirectory 'http-server-ready.txt'
$stopFile = Join-Path $OutputDirectory 'http-server-stop.txt'
$flowRoot = Join-Path $OutputDirectory 'flow'
$runToken = "connectedremoteimage$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
$remoteImageUrl = "http://127.0.0.1:$port/logo.png?token=$runToken"
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
Remove-Item -LiteralPath $serverLog -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $readyFile -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $stopFile -Force -ErrorAction SilentlyContinue

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = @(& $adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: $($Arguments -join ' ')`n$($result -join "`n")"
    }
    $result
}

$serverJob = Start-Job -ScriptBlock {
    param($Port, $ImagePath, $LogPath, $ReadyPath, $StopPath)
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
                do { $line = $reader.ReadLine() } while ($null -ne $line -and $line.Length -gt 0)
                Add-Content -LiteralPath $LogPath -Encoding utf8 -Value $requestLine
                $bytes = [IO.File]::ReadAllBytes($ImagePath)
                $header = "HTTP/1.1 200 OK`r`nContent-Type: image/png`r`n" +
                    "Content-Length: $($bytes.Length)`r`nConnection: close`r`n`r`n"
                $headerBytes = [Text.Encoding]::ASCII.GetBytes($header)
                $stream.Write($headerBytes, 0, $headerBytes.Length)
                $stream.Write($bytes, 0, $bytes.Length)
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
} -ArgumentList $port, $imagePath, $serverLog, $readyFile, $stopFile

try {
    $deadline = (Get-Date).AddSeconds(15)
    do { Start-Sleep -Milliseconds 100 } while (
        -not (Test-Path -LiteralPath $readyFile) -and (Get-Date) -lt $deadline
    )
    if (-not (Test-Path -LiteralPath $readyFile)) {
        throw 'Loopback image fixture did not start.'
    }

    Adb '-s' $Serial reverse "tcp:$port" "tcp:$port" | Out-Null
    & (Join-Path $PSScriptRoot 'verify-rich-content-targets.ps1') `
        -Serial $Serial -OutputDirectory $flowRoot -RemoteImageUrl $remoteImageUrl `
        -RejectedProductionImageUrl "$remoteImageUrl-production-policy"
    Start-Sleep -Milliseconds 500
} finally {
    try { Adb '-s' $Serial reverse '--remove' "tcp:$port" | Out-Null } catch { }
    Set-Content -LiteralPath $stopFile -Encoding utf8 -Value 'stop'
    $null = Wait-Job -Job $serverJob -Timeout 5
    if ($serverJob.State -notin @('Completed', 'Failed', 'Stopped')) {
        Stop-Job -Job $serverJob -ErrorAction SilentlyContinue
    }
    Remove-Job -Job $serverJob -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $stopFile -Force -ErrorAction SilentlyContinue
}

$requests = if (Test-Path -LiteralPath $serverLog) {
    @(Get-Content -LiteralPath $serverLog)
} else { @() }
$acceptRequests = @($requests | Where-Object {
    $_ -match "token=$([regex]::Escape($runToken))-accept\s+HTTP/"
}).Count
$rejectRequests = @($requests | Where-Object {
    $_ -match "token=$([regex]::Escape($runToken))-reject\s+HTTP/"
}).Count
$unsupportedRequests = @($requests | Where-Object {
    $_ -match "token=$([regex]::Escape($runToken))-unsupported\s+HTTP/"
}).Count
$productionPolicyRequests = @($requests | Where-Object {
    $_ -match "token=$([regex]::Escape($runToken))-production-policy\s+HTTP/"
}).Count
if ($acceptRequests -ne 1 -or $rejectRequests -ne 1 -or $unsupportedRequests -ne 0 -or
    $productionPolicyRequests -ne 0) {
    throw "Unexpected remote-image request matrix: accept=$acceptRequests reject=$rejectRequests unsupported=$unsupportedRequests productionPolicy=$productionPolicyRequests"
}
$flow = Get-Content -Raw -LiteralPath (Join-Path $flowRoot 'summary.json') | ConvertFrom-Json
[ordered]@{
    generatedAt = (Get-Date).ToString('o')
    overall = 'PASS'
    serial = $Serial
    transport = 'adb-reversed loopback HTTP; debug-only cleartext fixture'
    runToken = $runToken
    acceptRequests = $acceptRequests
    rejectRequests = $rejectRequests
    unsupportedRequests = $unsupportedRequests
    productionPolicyRequests = $productionPolicyRequests
    uidRxDelta = $flow.uidRxDelta
    uidTxDelta = $flow.uidTxDelta
    controlledTextOccurrencesInUidLog = $flow.controlledTextOccurrencesInUidLog
    flowSummary = $flow
    claimBoundary = 'deterministic remote fetch/fallback attribution; production rejects cleartext sources before fetch; approved production host ownership remains pending'
} | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 `
    (Join-Path $OutputDirectory 'summary.json')
Write-Host "Remote rich-content attribution passed: $OutputDirectory"
