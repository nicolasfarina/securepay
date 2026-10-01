$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $repoRoot

try {
    $apiPort = if ($env:SECUREPAY_API_PORT) { $env:SECUREPAY_API_PORT } else { "8080" }

    & docker compose up --build -d
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose could not start SecurePay."
    }

    $readinessUrl = "http://localhost:$apiPort/actuator/health/readiness"
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri $readinessUrl -TimeoutSec 3
            if ($response.StatusCode -eq 200) {
                Write-Host "SecurePay is ready at http://localhost:$apiPort"
                Write-Host "Swagger UI: http://localhost:$apiPort/swagger-ui.html"
                Write-Host "Stop it with: docker compose down"
                return
            }
        }
        catch {
            Start-Sleep -Seconds 2
        }
    }

    & docker compose logs --tail 100 api postgres
    throw "SecurePay did not become ready within 120 seconds."
}
finally {
    Pop-Location
}
