param([string]$OutputPath = '')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $OutputPath) { $OutputPath = Join-Path $root 'deliverables/SpeakPen-cloud-deploy.zip' }
$stage = Join-Path $env:TEMP ('SpeakPen-cloud-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage -Force | Out-Null
# Explicit allowlist: never include .env, .local, provider keys, APKs or APKGs.
foreach ($relative in @('server/src.mjs','server/package.json','server/.env.example','deploy','docs/CLOUD-DEPLOYMENT.md','docs/CLOUD-TTS-2026-11-09.md','docs/CLOUD-ANKI-2026-11-08.md','docs/CLOUD-LIVE-2026-11-09.md','docs/research/IP-HTTPS-2026-11-09.md')) {
    $target = Join-Path $stage $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $root $relative) -Destination $target -Recurse
}
New-Item -ItemType Directory -Path (Join-Path $stage 'server/public') -Force | Out-Null
foreach ($name in @('index.html','app.js','app.css','learning.js')) {
    Copy-Item -LiteralPath (Join-Path $root "server/public/$name") -Destination (Join-Path $stage "server/public/$name")
}
New-Item -ItemType Directory -Path (Split-Path -Parent $OutputPath) -Force | Out-Null
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $OutputPath -Force
Get-FileHash -LiteralPath $OutputPath -Algorithm SHA256
# Retain the narrow staging directory for inspection; it contains no credentials.
Write-Output "部署包: $OutputPath"
