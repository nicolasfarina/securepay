$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$project = 'securepay-dast-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$scanExit = 1
Push-Location $repoRoot
try {
    New-Item -ItemType Directory -Force dast/reports | Out-Null
    docker compose -p $project -f compose.dast.yaml run --rm --no-deps zap python3 -m unittest discover -s /zap/wrk -p 'test_*.py'
    if ($LASTEXITCODE -ne 0) { throw 'DAST safety tests failed' }
    docker compose -p $project -f compose.dast.yaml build api
    if ($LASTEXITCODE -ne 0) { throw 'Disposable API build failed' }
    docker compose -p $project -f compose.dast.yaml up --abort-on-container-exit --exit-code-from zap
    $scanExit = $LASTEXITCODE
}
finally {
    docker compose -p $project -f compose.dast.yaml down --volumes --remove-orphans
    if ($LASTEXITCODE -ne 0) { $scanExit = 1 }
    Pop-Location
}
exit $scanExit
