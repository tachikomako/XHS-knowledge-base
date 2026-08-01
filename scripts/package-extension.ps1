$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$extensionRoot = Join-Path $repoRoot 'extension'
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $extensionRoot 'manifest.json') | ConvertFrom-Json
$version = $manifest.version
$dist = Join-Path $repoRoot 'dist'
$stage = Join-Path $dist 'extension-package'
$zip = Join-Path $dist "xhs-knowledge-extension-$version.zip"

New-Item -ItemType Directory -Force -Path $dist | Out-Null
if (Test-Path -LiteralPath $stage) {
    Remove-Item -LiteralPath $stage -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $stage | Out-Null

Copy-Item -LiteralPath (Join-Path $extensionRoot 'manifest.json') -Destination $stage
Copy-Item -LiteralPath (Join-Path $extensionRoot 'background') -Destination $stage -Recurse
Copy-Item -LiteralPath (Join-Path $extensionRoot 'content') -Destination $stage -Recurse
Copy-Item -LiteralPath (Join-Path $extensionRoot 'popup') -Destination $stage -Recurse

if (Test-Path -LiteralPath $zip) {
    Remove-Item -LiteralPath $zip -Force
}
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip
Remove-Item -LiteralPath $stage -Recurse -Force

Write-Host "Created $zip"
