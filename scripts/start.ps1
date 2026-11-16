[CmdletBinding()]
param(
    [switch]$WithAntivirus,
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root '.env'
$ComposeFile = Join-Path $Root 'deploy\compose.yaml'

& (Join-Path $PSScriptRoot 'bootstrap-check.ps1')

$argsList = @('compose', '--env-file', $EnvFile, '-f', $ComposeFile)
if ($WithAntivirus) { $argsList += @('--profile', 'antivirus') }
$argsList += @('up', '-d')
if (-not $NoBuild) { $argsList += '--build' }

& docker @argsList
if ($LASTEXITCODE -ne 0) { throw 'Startup failed. Check docker compose logs.' }

& docker compose --env-file $EnvFile -f $ComposeFile ps
Write-Host 'family-tree is running. Default URL: http://localhost/'
