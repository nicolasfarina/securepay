#Requires -Version 7.0
param(
    [string]$ImageTag = 'securepay:day12-local',
    [switch]$SkipBuild
)

$previousErrorPreference = $ErrorActionPreference
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 12)
$network = "securepay-image-$suffix"
$postgres = "securepay-db-$suffix"
$api = "securepay-api-$suffix"
$networkCreated = $false
$postgresCreated = $false
$apiCreated = $false

function Invoke-DockerChecked([string[]]$CommandArguments) {
    $result = & docker @CommandArguments
    if ($LASTEXITCODE -ne 0) { throw "Docker operation failed: $($CommandArguments[0])" }
    return $result
}

function Assert-Status($Response, [int]$Expected) {
    if ($Response.StatusCode -ne $Expected) {
        throw "Expected HTTP $Expected, received $($Response.StatusCode): $($Response.Content)"
    }
}

function Get-ResponseText($Response) {
    if ($Response.Content -is [byte[]]) { return [Text.Encoding]::UTF8.GetString($Response.Content) }
    return [string]$Response.Content
}

Push-Location $repoRoot
try {
    Get-Command docker -ErrorAction Stop | Out-Null
    Invoke-DockerChecked @('info', '--format', '{{.ServerVersion}}') | Out-Null
    New-Item -ItemType Directory -Force 'target/day12-evidence' | Out-Null
    if (-not $SkipBuild) {
        & docker build --progress=plain -t $ImageTag . 2>&1 | Tee-Object 'target/day12-evidence/build.log'
        if ($LASTEXITCODE -ne 0) { throw 'The image build or its Maven tests failed.' }
    }

    $databasePassword = [guid]::NewGuid().ToString('N')
    $demoPassword = [guid]::NewGuid().ToString('N')
    $jwtSecret = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    Invoke-DockerChecked @('network', 'create', $network) | Out-Null
    $networkCreated = $true
    Invoke-DockerChecked @('run', '--detach', '--name', $postgres, '--network', $network,
        '-e', 'POSTGRES_DB=securepay_test', '-e', 'POSTGRES_USER=securepay_test',
        '-e', "POSTGRES_PASSWORD=$databasePassword", 'postgres:17') | Out-Null
    $postgresCreated = $true
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & docker exec $postgres pg_isready -h 127.0.0.1 -U securepay_test -d securepay_test 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Test PostgreSQL did not become ready.' }

    Invoke-DockerChecked @('run', '--detach', '--name', $api, '--network', $network,
        '-p', '127.0.0.1::8080', '--read-only', '--tmpfs', '/tmp:rw,nosuid,nodev,size=64m',
        '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges', '--memory', '768m',
        '-e', 'SPRING_PROFILES_ACTIVE=prod',
        '-e', "SECUREPAY_DATABASE_URL=jdbc:postgresql://${postgres}:5432/securepay_test",
        '-e', 'SECUREPAY_DATABASE_USERNAME=securepay_test', '-e', "SECUREPAY_DATABASE_PASSWORD=$databasePassword",
        '-e', "SECUREPAY_DEMO_PASSWORD=$demoPassword", '-e', "SECUREPAY_JWT_SECRET=$jwtSecret", $ImageTag) | Out-Null
    $apiCreated = $true
    $binding = Invoke-DockerChecked @('port', $api, '8080/tcp')
    if ($binding -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Unexpected API port binding.' }
    $baseUrl = "http://127.0.0.1:$($Matches[1])"

    $ready = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        try {
            $health = Invoke-RestMethod "$baseUrl/actuator/health/readiness" -TimeoutSec 2
            if ($health.status -eq 'UP') {
                $ready = $true
                break
            }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'The production-profile API did not become ready.' }

    $userId = (Invoke-DockerChecked @('exec', $api, 'id', '-u')).Trim()
    if ($userId -ne '10001') { throw "Unexpected runtime user: $userId" }
    $readOnly = Invoke-DockerChecked @('inspect', '--format', '{{.HostConfig.ReadonlyRootfs}}', $api)
    if ($readOnly -ne 'true') { throw 'The root filesystem is not read-only.' }
    $pidOne = (Invoke-DockerChecked @('exec', $api, 'cat', '/proc/1/comm')).Trim()
    if ($pidOne -ne 'java') { throw 'Java is not the container PID 1.' }
    foreach ($path in @('/v3/api-docs', '/swagger-ui/index.html')) {
        Assert-Status (Invoke-WebRequest "$baseUrl$path" -SkipHttpErrorCheck) 404
    }

    $payment = '{"merchantId":"merchant-456","amount":10.00,"currency":"USD"}'
    Assert-Status (Invoke-WebRequest "$baseUrl/api/payments" -Method Post -ContentType 'application/json' `
        -Headers @{'Idempotency-Key' = 'anonymous'} -Body $payment -SkipHttpErrorCheck) 401
    $loginBody = @{username = 'customer-123'; password = $demoPassword} | ConvertTo-Json -Compress
    $login = Invoke-RestMethod "$baseUrl/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody
    $headers = @{Authorization = "Bearer $($login.accessToken)"; 'Idempotency-Key' = 'container-smoke'}
    $created = Invoke-WebRequest "$baseUrl/api/payments" -Method Post -ContentType 'application/json' `
        -Headers $headers -Body $payment -SkipHttpErrorCheck
    Assert-Status $created 201
    $replay = Invoke-WebRequest "$baseUrl/api/payments" -Method Post -ContentType 'application/json' `
        -Headers $headers -Body $payment -SkipHttpErrorCheck
    Assert-Status $replay 201
    $createdPayment = Get-ResponseText $created | ConvertFrom-Json
    $replayedPayment = Get-ResponseText $replay | ConvertFrom-Json
    foreach ($field in @('id', 'customerId', 'merchantId', 'currency', 'status', 'createdAt', 'updatedAt')) {
        if ($createdPayment.$field -ne $replayedPayment.$field) { throw "Payment replay differs in $field." }
    }
    if ([decimal]$createdPayment.amount -ne [decimal]$replayedPayment.amount) { throw 'Payment replay amount differs.' }
    Assert-Status (Invoke-WebRequest "$baseUrl/api/payments" -Method Post -ContentType 'application/json' `
        -Headers $headers -Body ($payment.Replace('10.00', '20.00')) -SkipHttpErrorCheck) 409
    $headers['Idempotency-Key'] = 'negative-container-smoke'
    Assert-Status (Invoke-WebRequest "$baseUrl/api/payments" -Method Post -ContentType 'application/json' `
        -Headers $headers -Body ($payment.Replace('10.00', '-10')) -SkipHttpErrorCheck) 400
    Assert-Status (Invoke-WebRequest "$baseUrl/api/admin/payments" -Headers $headers -SkipHttpErrorCheck) 403
    $count = (Invoke-DockerChecked @('exec', $postgres, 'psql', '-U', 'securepay_test', '-d', 'securepay_test',
        '-At', '-c', 'SELECT count(*) FROM payments;')).Trim()
    $records = (Invoke-DockerChecked @('exec', $postgres, 'psql', '-U', 'securepay_test', '-d', 'securepay_test',
        '-At', '-c', 'SELECT count(*) FROM idempotency_records;')).Trim()
    if ($count -ne '1' -or $records -ne '1') { throw 'Unexpected persisted rows after replay and rejected requests.' }

    Invoke-DockerChecked @('stop', '--time', '30', $api) | Out-Null
    $exitCode = [int](Invoke-DockerChecked @('inspect', '--format', '{{.State.ExitCode}}', $api))
    $oomKilled = Invoke-DockerChecked @('inspect', '--format', '{{.State.OOMKilled}}', $api)
    if ($exitCode -notin @(0, 143) -or $oomKilled -ne 'false') { throw 'API did not stop cleanly with SIGTERM.' }
    $imageId = Invoke-DockerChecked @('image', 'inspect', '--format', '{{.Id}}', $ImageTag)
    $imageBytes = [long](Invoke-DockerChecked @('image', 'inspect', '--format', '{{.Size}}', $ImageTag))
    [pscustomobject]@{ image = $ImageTag; imageId = $imageId; imageBytes = $imageBytes;
        profile = 'prod'; userId = $userId; javaPidOne = $true; readOnlyRoot = $true;
        paymentRows = [int]$count; idempotencyRows = [int]$records; stopExitCode = $exitCode;
        result = 'PASS' } | ConvertTo-Json | Tee-Object 'target/day12-evidence/container-results.json'
}
finally {
    if ($apiCreated) {
        & docker logs $api *> 'target/day12-evidence/api.log'
        & docker rm --force --volumes $api | Out-Null
    }
    if ($postgresCreated) { & docker rm --force --volumes $postgres | Out-Null }
    if ($networkCreated) { & docker network rm $network | Out-Null }
    Pop-Location
    $ErrorActionPreference = $previousErrorPreference
}
