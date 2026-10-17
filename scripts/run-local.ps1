#Requires -Version 7.0
param(
    [ValidateRange(1, 65535)]
    [int]$ApiPort = $(if ($env:SECUREPAY_API_PORT) { [int]$env:SECUREPAY_API_PORT } else { 8080 }),
    [ValidatePattern('^[a-z0-9][a-z0-9_-]*$')]
    [string]$ProjectName
)

$previousErrorPreference = $ErrorActionPreference
$ErrorActionPreference = "Stop"
$previousApiPort = $env:SECUREPAY_API_PORT

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $repoRoot

try {
    $env:SECUREPAY_API_PORT = [string]$ApiPort
    $composeArguments = @('compose')
    if ($ProjectName) { $composeArguments += @('--project-name', $ProjectName) }
    & docker @composeArguments up --build --wait --wait-timeout 120
    if ($LASTEXITCODE -ne 0) {
        & docker @composeArguments logs --tail 60 api postgres
        throw "Docker Compose could not make both services healthy. Check port availability and the service logs."
    }

    Write-Host "SecurePay and PostgreSQL are healthy."
    Write-Host "Swagger UI: http://127.0.0.1:$ApiPort/swagger-ui/index.html"
    if ($ProjectName) { Write-Host "Stop and preserve data: docker compose --project-name $ProjectName down" }
    else { Write-Host "Stop and preserve data: docker compose down" }
}
finally {
    Pop-Location
    [Environment]::SetEnvironmentVariable('SECUREPAY_API_PORT', $previousApiPort, 'Process')
    $ErrorActionPreference = $previousErrorPreference
}
