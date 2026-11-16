[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root '.env'
$ComposeFile = Join-Path $Root 'deploy\compose.yaml'

if ((Split-Path -Leaf $Root) -cne 'family-tree') {
    throw "The project directory must be named family-tree. Current name: $(Split-Path -Leaf $Root)"
}

if (-not (Get-Command 'docker' -ErrorAction SilentlyContinue)) {
    throw 'Docker was not found. Install Docker Desktop and make sure docker is in PATH.'
}

docker version | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker is unavailable. Start Docker Desktop.' }
docker compose version | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose v2 is unavailable.' }

if (-not (Test-Path $EnvFile)) {
    throw 'Missing .env. Run: Copy-Item .env.example .env, then replace every CHANGE_ME value.'
}

$envText = [IO.File]::ReadAllText($EnvFile)
if ($envText -match 'CHANGE_ME') { throw '.env still contains CHANGE_ME placeholders.' }

$required = @('MYSQL_DATABASE', 'MYSQL_USER', 'MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'ADMIN_PASSWORD', 'APP_DATA_ENCRYPTION_KEY')
foreach ($name in $required) {
    if ($envText -notmatch "(?m)^$([regex]::Escape($name))=.+$") {
        throw ".env is missing a non-empty variable: $name"
    }
}

docker compose --env-file $EnvFile -f $ComposeFile config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose configuration validation failed.' }

Write-Host 'Checks passed: directory, Docker, environment, and Compose configuration are valid.'
