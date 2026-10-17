#Requires -Version 7.0
param([switch]$SkipBuild)

$previousErrorPreference = $ErrorActionPreference
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$project = 'securepay-check-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$names = @('SECUREPAY_API_PORT', 'SECUREPAY_IMAGE', 'SECUREPAY_DATABASE_PASSWORD',
    'SECUREPAY_DEMO_PASSWORD', 'SECUREPAY_JWT_SECRET')
$previousEnvironment = @{}
foreach ($name in $names) { $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$started = $false

function Invoke-Compose([string[]]$CommandArguments) {
    $result = & docker compose --project-name $project @CommandArguments
    if ($LASTEXITCODE -ne 0) { throw "Compose operation failed: $($CommandArguments[0])" }
    return $result
}

function Get-ApiUrl {
    $binding = Invoke-Compose @('port', 'api', '8080')
    if ($binding -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Unexpected API port binding.' }
    return "http://127.0.0.1:$($Matches[1])"
}

function Wait-ForHealthy([string]$Service, [int]$TimeoutSeconds = 120) {
    $containerId = Invoke-Compose @('ps', '-q', $Service)
    if ([string]::IsNullOrWhiteSpace($containerId)) { throw "Compose did not return a container for $Service." }
    for ($attempt = 0; $attempt -lt $TimeoutSeconds; $attempt++) {
        $health = & docker inspect --format '{{.State.Health.Status}}' $containerId
        if ($LASTEXITCODE -ne 0) { throw "Could not inspect $Service health." }
        if ($health -eq 'healthy') { return }
        $state = & docker inspect --format '{{.State.Status}}' $containerId
        if ($LASTEXITCODE -ne 0 -or $state -eq 'exited' -or $state -eq 'dead') {
            throw "$Service stopped before becoming healthy (state: $state, health: $health)."
        }
        Start-Sleep -Seconds 1
    }
    throw "$Service did not become healthy within $TimeoutSeconds seconds."
}

function Assert-Status($Response, [int]$Expected) {
    if ($Response.StatusCode -ne $Expected) { throw "Expected HTTP $Expected, got $($Response.StatusCode)." }
}

function Get-CustomerHeaders {
    $body = @{username = 'customer-123'; password = $demoPassword} | ConvertTo-Json -Compress
    $login = Invoke-RestMethod "$baseUrl/auth/login" -Method Post -ContentType 'application/json' -Body $body
    return @{Authorization = "Bearer $($login.accessToken)"; 'Idempotency-Key' = 'compose-persistence'}
}

function Assert-PaymentExists {
    $payment = Invoke-RestMethod "$baseUrl/api/payments/$paymentId" -Headers (Get-CustomerHeaders)
    if ($payment.id -ne $paymentId -or [decimal]$payment.amount -ne 10) { throw 'The original payment was not preserved.' }
}

Push-Location $repoRoot
try {
    Get-Command docker -ErrorAction Stop | Out-Null
    $env:SECUREPAY_API_PORT = '0'
    $env:SECUREPAY_IMAGE = 'securepay:day13-local'
    $env:SECUREPAY_DATABASE_PASSWORD = [guid]::NewGuid().ToString('N')
    $demoPassword = [guid]::NewGuid().ToString('N')
    $env:SECUREPAY_DEMO_PASSWORD = $demoPassword
    $env:SECUREPAY_JWT_SECRET = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    New-Item -ItemType Directory -Force 'target/day13-evidence' | Out-Null
    Invoke-Compose @('config', '--quiet') | Out-Null
    $buildFlag = if ($SkipBuild) { '--no-build' } else { '--build' }
    $started = $true
    Invoke-Compose @('up', $buildFlag, '--wait', '--wait-timeout', '120') | Out-Null
    $baseUrl = Get-ApiUrl
    Write-Host 'Both Compose services are healthy. Creating a payment.'
    $headers = Get-CustomerHeaders
    $payload = '{"merchantId":"merchant-456","amount":10.00,"currency":"USD"}'
    $created = Invoke-WebRequest "$baseUrl/api/payments" -Method Post -Headers $headers `
        -ContentType 'application/json' -Body $payload -SkipHttpErrorCheck
    Assert-Status $created 201
    # The JSON endpoint uses application/json; RestMethod also handles any byte-array response bodies.
    $paymentId = (Invoke-RestMethod "$baseUrl/api/payments" -Method Post -Headers $headers `
        -ContentType 'application/json' -Body $payload).id
    $databaseId = Invoke-Compose @('ps', '-q', 'postgres')
    $volume = & docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' $databaseId
    if ($LASTEXITCODE -ne 0 -or -not $volume.StartsWith($project + '_')) { throw 'Unexpected database volume ownership.' }

    Write-Host 'Recreating only the API container; the stored payment must remain.'
    Invoke-Compose @('up', '--force-recreate', '--no-deps', '--no-build', '-d', 'api') | Out-Null
    Wait-ForHealthy 'api'
    $baseUrl = Get-ApiUrl
    Assert-PaymentExists

    Write-Host 'Removing both containers, preserving the volume, then starting them again.'
    Invoke-Compose @('down') | Out-Null
    & docker volume inspect $volume --format '{{.Name}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Compose down unexpectedly removed the database volume.' }
    Invoke-Compose @('up', '--no-build', '-d') | Out-Null
    Wait-ForHealthy 'postgres'
    Wait-ForHealthy 'api'
    $baseUrl = Get-ApiUrl
    Assert-PaymentExists
    $replay = Invoke-RestMethod "$baseUrl/api/payments" -Method Post -Headers (Get-CustomerHeaders) `
        -ContentType 'application/json' -Body $payload
    if ($replay.id -ne $paymentId) { throw 'The persisted idempotency record was not reused.' }

    Write-Host 'Stopping PostgreSQL: readiness must fail while liveness stays UP.'
    Invoke-Compose @('stop', 'postgres') | Out-Null
    $unready = Invoke-WebRequest "$baseUrl/actuator/health/readiness" -TimeoutSec 15 -SkipHttpErrorCheck
    Assert-Status $unready 503
    $live = Invoke-RestMethod "$baseUrl/actuator/health/liveness" -TimeoutSec 5
    if ($live.status -ne 'UP') { throw 'Database outage incorrectly failed application liveness.' }
    $apiId = Invoke-Compose @('ps', '-q', 'api')
    $unhealthy = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $health = & docker inspect --format '{{.State.Health.Status}}' $apiId
        if ($LASTEXITCODE -ne 0) { throw 'Could not inspect API health.' }
        if ($health -eq 'unhealthy') { $unhealthy = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $unhealthy) { throw 'Compose API health did not reflect the database outage.' }

    Write-Host 'Restarting PostgreSQL and waiting for both services to recover.'
    Invoke-Compose @('up', '--no-build', '-d') | Out-Null
    Wait-ForHealthy 'postgres'
    Wait-ForHealthy 'api'
    Assert-PaymentExists
    $paymentCount = (Invoke-Compose @('exec', '-T', 'postgres', 'psql', '-U', 'securepay', '-d', 'securepay',
        '-At', '-c', 'SELECT count(*) FROM payments;')).Trim()
    $recordCount = (Invoke-Compose @('exec', '-T', 'postgres', 'psql', '-U', 'securepay', '-d', 'securepay',
        '-At', '-c', 'SELECT count(*) FROM idempotency_records;')).Trim()
    if ($paymentCount -ne '1' -or $recordCount -ne '1') { throw 'Unexpected database counts after recovery.' }
    [pscustomobject]@{ result = 'PASS'; paymentId = $paymentId; volume = $volume;
        apiReplacementPreservedPayment = $true; stackReplacementPreservedPayment = $true;
        outageReadiness = 503; outageLiveness = 'UP'; outageContainerHealth = 'unhealthy';
        recovered = $true; paymentRows = [int]$paymentCount; idempotencyRows = [int]$recordCount
    } | ConvertTo-Json | Tee-Object 'target/day13-evidence/compose-results.json'
}
finally {
    if ($started) {
        & docker compose --project-name $project logs --no-color *> 'target/day13-evidence/services.log'
        # This unique project contains only disposable verification data.
        & docker compose --project-name $project down --volumes --remove-orphans | Out-Null
    }
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process') }
    Pop-Location
    $ErrorActionPreference = $previousErrorPreference
}
