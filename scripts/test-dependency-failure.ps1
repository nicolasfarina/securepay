#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$project = 'securepay-day43-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$previous = @{}
$names = @('SECUREPAY_API_PORT','SECUREPAY_IMAGE','SECUREPAY_DATABASE_PASSWORD','SECUREPAY_DEMO_PASSWORD','SECUREPAY_JWT_SECRET')
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$started = $false

function Invoke-Compose([string[]]$ArgsList) {
    $result = & docker compose --project-name $project @ArgsList
    if ($LASTEXITCODE -ne 0) { throw "Compose failed: $($ArgsList[0])" }
    return $result
}

function Wait-Healthy([string]$Service, [int]$TimeoutSeconds = 90) {
    $id = (Invoke-Compose @('ps','-q',$Service)).Trim()
    if (-not $id) { throw "Missing $Service container." }
    for ($i = 0; $i -lt $TimeoutSeconds; $i++) {
        $health = & docker inspect --format '{{.State.Health.Status}}' $id
        if ($LASTEXITCODE -ne 0) { throw "Cannot inspect $Service health." }
        if ($health -eq 'healthy') { return $id }
        Start-Sleep -Seconds 1
    }
    throw "$Service did not become healthy."
}

Push-Location $repoRoot
try {
    $env:SECUREPAY_API_PORT = '0'
    $env:SECUREPAY_IMAGE = 'securepay:day43-local'
    $env:SECUREPAY_DATABASE_PASSWORD = [guid]::NewGuid().ToString('N')
    $env:SECUREPAY_DEMO_PASSWORD = [guid]::NewGuid().ToString('N')
    $env:SECUREPAY_JWT_SECRET = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    & docker compose --project-name $project config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Compose configuration is invalid.' }
    $started = $true
    Invoke-Compose @('up','--build','--wait','--wait-timeout','180') | Out-Null
    $postgresId = Wait-Healthy 'postgres'
    $apiId = (Invoke-Compose @('ps','-q','api')).Trim()
    $apiPort = (Invoke-Compose @('port','api','8080')).Trim()
    if ($apiPort -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'API port is not bound to loopback.' }
    $baseUrl = "http://127.0.0.1:$($Matches[1])"

    $loginBody = @{username='customer-123';password=$env:SECUREPAY_DEMO_PASSWORD} | ConvertTo-Json -Compress
    $token = (Invoke-RestMethod "$baseUrl/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody).accessToken
    $headers = @{Authorization="Bearer $token";'Idempotency-Key'=('day43-'+[guid]::NewGuid().ToString('N'))}
    $payload = '{"merchantId":"day43-test","amount":12.50,"currency":"USD"}'
    $created = Invoke-RestMethod "$baseUrl/api/payments" -Method Post -Headers $headers -ContentType 'application/json' -Body $payload
    $paymentId = $created.id

    $network = (& docker inspect --format '{{range $name, $value := .NetworkSettings.Networks}}{{$name}}{{end}}' $postgresId).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $network.StartsWith($project + '_')) { throw 'Unexpected disposable network.' }
    Write-Host 'Disconnecting only PostgreSQL from this disposable project network.'
    & docker network disconnect $network $postgresId
    if ($LASTEXITCODE -ne 0) { throw 'Could not isolate PostgreSQL from the API.' }
    Start-Sleep -Seconds 2

    $readiness = Invoke-WebRequest "$baseUrl/actuator/health/readiness" -TimeoutSec 10 -SkipHttpErrorCheck
    if ($readiness.StatusCode -ne 503) { throw "Expected readiness 503 during partition, got $($readiness.StatusCode)." }
    $liveness = Invoke-RestMethod "$baseUrl/actuator/health/liveness" -TimeoutSec 10
    if ($liveness.status -ne 'UP') { throw 'Database network partition incorrectly failed liveness.' }
    $dbState = (& docker inspect --format '{{.State.Status}}' $postgresId).Trim()
    if ($dbState -ne 'running') { throw "PostgreSQL process should remain running; got $dbState." }
    $apiState = (& docker inspect --format '{{.State.Status}}' $apiId).Trim()
    if ($apiState -ne 'running') { throw "API process should remain running; got $apiState." }

    Write-Host 'Restoring PostgreSQL network access and waiting for recovery.'
    & docker network connect --alias postgres $network $postgresId
    if ($LASTEXITCODE -ne 0) { throw 'Could not restore PostgreSQL network access.' }
    Wait-Healthy 'postgres' | Out-Null
    Wait-Healthy 'api' | Out-Null
    $recovered = Invoke-WebRequest "$baseUrl/actuator/health/readiness" -TimeoutSec 10 -SkipHttpErrorCheck
    if ($recovered.StatusCode -ne 200) { throw "Expected readiness 200 after recovery, got $($recovered.StatusCode)." }
    $payment = Invoke-RestMethod "$baseUrl/api/payments/$paymentId" -Headers @{Authorization="Bearer $token"}
    if ($payment.id -ne $paymentId -or [decimal]$payment.amount -ne 12.50) { throw 'Payment did not survive dependency recovery.' }

    [pscustomobject]@{result='PASS';fault='PostgreSQL network partition';postgresProcess=$dbState;
        apiProcess=$apiState;readinessDuringFault=$readiness.StatusCode;livenessDuringFault=$liveness.status;
        readinessAfterRecovery=$recovered.StatusCode;paymentPreserved=$true;deployment='local disposable Compose only'} |
        ConvertTo-Json | Tee-Object 'target/day43-dependency-failure.json'
}
finally {
    if ($started) { & docker compose --project-name $project down --volumes --remove-orphans | Out-Null }
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    Pop-Location
}
