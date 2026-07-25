$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

Push-Location (Join-Path $repoRoot 'backend')
try {
    & .\mvnw.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed' }
} finally {
    Pop-Location
}

Push-Location (Join-Path $repoRoot 'frontend')
try {
    npm run test
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed' }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
    npm audit --audit-level=high
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency audit failed' }
} finally {
    Pop-Location
}

Get-ChildItem -LiteralPath (Join-Path $repoRoot 'extension') -Recurse -Filter '*.js' |
    ForEach-Object {
        node --check $_.FullName
        if ($LASTEXITCODE -ne 0) { throw "Extension syntax check failed: $($_.FullName)" }
    }

Get-Content -LiteralPath (Join-Path $repoRoot 'extension\manifest.json') -Raw -Encoding UTF8 |
    ConvertFrom-Json |
    Out-Null

Write-Host 'All checks passed.' -ForegroundColor Green
