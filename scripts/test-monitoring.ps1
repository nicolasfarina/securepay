#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 12)
$project = "securepay-monitoring-$suffix"
$image = "securepay:monitoring-$suffix"
$tempDir = Join-Path ([IO.Path]::GetTempPath()) $project
$tokenFile = Join-Path $tempDir 'token'
$names = @('SECUREPAY_API_PORT', 'SECUREPAY_PROMETHEUS_PORT', 'SECUREPAY_RECEIVER_PORT', 'SECUREPAY_IMAGE',
    'SECUREPAY_DATABASE_PASSWORD', 'SECUREPAY_DEMO_PASSWORD', 'SECUREPAY_JWT_SECRET',
    'SECUREPAY_PROMETHEUS_TOKEN_FILE')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$started = $false
function Invoke-Compose([string[]]$Arguments) {
    $output = & docker compose --project-name $project -f compose.yaml -f compose.monitoring.yaml @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Compose failed: $($Arguments[0])" }
    return $output
}
function Get-LocalPort([string]$Service, [string]$Port) {
    $binding = Invoke-Compose @('port', $Service, $Port)
    if ($binding -notmatch '^127\.0\.0\.1:(\d+)$') { throw "Unexpected port binding for $Service." }
    return $Matches[1]
}
try {
    New-Item -ItemType Directory -Path $tempDir | Out-Null
    [IO.File]::WriteAllText($tokenFile, '')
    $env:SECUREPAY_API_PORT = '0'
    $env:SECUREPAY_PROMETHEUS_PORT = '0'
    $env:SECUREPAY_RECEIVER_PORT = '0'
    $env:SECUREPAY_IMAGE = $image
    $env:SECUREPAY_DATABASE_PASSWORD = [guid]::NewGuid().ToString('N')
    $env:SECUREPAY_DEMO_PASSWORD = [guid]::NewGuid().ToString('N')
    $env:SECUREPAY_JWT_SECRET = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $env:SECUREPAY_PROMETHEUS_TOKEN_FILE = $tokenFile
    Push-Location $repoRoot
    try {
        $started = $true
        Invoke-Compose @('up', '-d', '--build', 'api') | Out-Null
        $apiPort = Get-LocalPort 'api' '8080'
        $baseUrl = "http://127.0.0.1:$apiPort"
        $ready = $false
        for ($i = 0; $i -lt 120; $i++) {
            try {
                if ((Invoke-WebRequest "$baseUrl/actuator/health/readiness" -TimeoutSec 2).StatusCode -eq 200) {
                    $ready = $true; break
                }
            } catch { }
            Start-Sleep -Seconds 1
        }
        if (-not $ready) { throw 'API did not become ready.' }
        $loginBody = @{ username = 'admin-123'; password = $env:SECUREPAY_DEMO_PASSWORD } | ConvertTo-Json -Compress
        $login = Invoke-RestMethod "$baseUrl/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody
        if ([string]::IsNullOrWhiteSpace($login.accessToken)) { throw 'No scrape token returned.' }
        [IO.File]::WriteAllText($tokenFile, $login.accessToken)
        Invoke-Compose @('up', '-d', '--build', 'prometheus') | Out-Null
        $promPort = Get-LocalPort 'prometheus' '9090'
        $receiverPort = Get-LocalPort 'alert-receiver' '8080'
        $scrapeOk = $false
        for ($i = 0; $i -lt 90; $i++) {
            try {
                $result = Invoke-RestMethod "http://127.0.0.1:$promPort/api/v1/query?query=up%7Bjob%3D%22securepay%22%7D" -TimeoutSec 2
                if ($result.status -eq 'success' -and $result.data.result.Count -eq 1 -and
                    $result.data.result[0].value[1] -eq '1') { $scrapeOk = $true; break }
            } catch { }
            Start-Sleep -Seconds 1
        }
        if (-not $scrapeOk) { throw 'Prometheus did not successfully scrape the API.' }
        $bucketQuery = [Uri]::EscapeDataString('count(http_server_requests_seconds_bucket{le="0.5"})')
        $buckets = Invoke-RestMethod "http://127.0.0.1:$promPort/api/v1/query?query=$bucketQuery" -TimeoutSec 5
        if ($buckets.status -ne 'success' -or $buckets.data.result.Count -ne 1 -or
            [double]$buckets.data.result[0].value[1] -lt 1) {
            throw 'Prometheus did not receive the 500 ms HTTP latency bucket.'
        }
        Write-Host 'Prometheus successfully scraped the authenticated SecurePay endpoint.'
        Write-Host 'Prometheus received the 500 ms HTTP latency bucket.'
        Invoke-Compose @('stop', 'api') | Out-Null
        $received = $false
        for ($i = 0; $i -lt 240; $i += 3) {
            try {
                $events = @(Invoke-RestMethod "http://127.0.0.1:$receiverPort/received" -TimeoutSec 2)
                if ($events | Where-Object { $_.name -eq 'SecurePayScrapeFailed' -and $_.status -eq 'firing' }) {
                    $received = $true; break
                }
            } catch { }
            Start-Sleep -Seconds 3
        }
        if (-not $received) { throw 'The local receiver did not receive the scrape-failure alert.' }
        Write-Host 'Alertmanager delivered the firing scrape-failure alert to the local receiver.'
    } finally {
        if ($started) { Invoke-Compose @('down', '--volumes', '--remove-orphans', '--rmi', 'local') | Out-Null }
        Pop-Location
    }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    if (Test-Path -LiteralPath $tokenFile) { Remove-Item -LiteralPath $tokenFile -Force }
    if (Test-Path -LiteralPath $tempDir) { Remove-Item -LiteralPath $tempDir -Force }
    & docker image rm $image 2>$null | Out-Null
}
