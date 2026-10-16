$previousErrorPreference = $ErrorActionPreference
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$containerName = 'securepay-integration-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$environmentNames = @('SECUREPAY_POSTGRES_TEST', 'SECUREPAY_TEST_POSTGRES_URL',
    'SECUREPAY_TEST_POSTGRES_USERNAME', 'SECUREPAY_TEST_POSTGRES_PASSWORD')
$previousEnvironment = @{}
foreach ($name in $environmentNames) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$containerCreated = $false
Push-Location $repoRoot

try {
    Get-Command docker, java, mvn -ErrorAction Stop | Out-Null
    & docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop before running integration tests.' }

    $testPassword = [guid]::NewGuid().ToString('N')
    & docker run --detach --name $containerName `
        -e POSTGRES_DB=securepay_test -e POSTGRES_USER=securepay_test `
        -e "POSTGRES_PASSWORD=$testPassword" -p '127.0.0.1::5432' postgres:17 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the disposable PostgreSQL container.' }
    $containerCreated = $true

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        # TCP readiness excludes the temporary Unix-socket server used during initdb.
        & docker exec $containerName pg_isready -h 127.0.0.1 -U securepay_test -d securepay_test 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'The disposable PostgreSQL database did not become ready.' }

    $binding = & docker port $containerName 5432/tcp
    if ($LASTEXITCODE -ne 0 -or $binding -notmatch '^127\.0\.0\.1:(\d+)$') {
        throw 'Could not determine the loopback database port.'
    }
    $env:SECUREPAY_POSTGRES_TEST = 'true'
    $env:SECUREPAY_TEST_POSTGRES_URL = "jdbc:postgresql://127.0.0.1:$($Matches[1])/securepay_test"
    $env:SECUREPAY_TEST_POSTGRES_USERNAME = 'securepay_test'
    $env:SECUREPAY_TEST_POSTGRES_PASSWORD = $testPassword

    New-Item -ItemType Directory -Force 'target/day11-evidence' | Out-Null
    Write-Host 'Running the complete suite, including real HTTP and PostgreSQL concurrency tests.'
    & mvn -B '-Duser.timezone=UTC' verify 2>&1 | Tee-Object -FilePath 'target/day11-evidence/postgres-verify.log'
    if ($LASTEXITCODE -ne 0) { throw 'Integration verification failed. See target/day11-evidence/postgres-verify.log.' }
}
finally {
    if ($containerCreated) {
        & docker rm --force --volumes $containerName | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Warning "Could not remove test container $containerName" }
    }
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    Pop-Location
    $ErrorActionPreference = $previousErrorPreference
}
