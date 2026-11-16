[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root '.env'
$ComposeFile = Join-Path $Root 'compose.yaml'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker was not found. Install/start Docker Desktop in Linux containers mode, or Docker Engine with Compose v2.' }
& docker version | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker Engine is unavailable. Start Docker Desktop first.' }
& docker compose version | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose v2 is unavailable.' }
if (-not (Test-Path -LiteralPath $EnvFile)) { throw 'Missing .env. For an existing installation run node scripts/prepare-compose.mjs; otherwise copy .env.example to .env and configure it.' }
$Text = [IO.File]::ReadAllText($EnvFile)
if ($Text -match 'CHANGE_ME') { throw '.env still contains CHANGE_ME placeholders.' }
foreach ($Name in @('DB_URL','DB_USERNAME','DB_PASSWORD','BOOTSTRAP_ADMIN_PASSWORD','APP_DATA_ENCRYPTION_KEY')) {
    if ($Text -notmatch "(?m)^$([regex]::Escape($Name))=.+$") { throw "Missing environment variable: $Name" }
}
# Never print `docker compose config`: it includes expanded passwords and encryption keys.
& docker compose --env-file $EnvFile -f $ComposeFile config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose configuration validation failed.' }
Write-Host 'Checks passed: Docker Engine, Compose, environment and root compose.yaml.'
