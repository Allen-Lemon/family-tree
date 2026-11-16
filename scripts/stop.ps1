[CmdletBinding()]
param(
    [switch]$RemoveVolumes
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root '.env'
$ComposeFile = Join-Path $Root 'deploy\compose.yaml'

$argsList = @('compose', '--env-file', $EnvFile, '-f', $ComposeFile, 'down', '--remove-orphans')
if ($RemoveVolumes) {
    if ($env:FAMILY_TREE_ALLOW_VOLUME_DELETE -ne 'YES') {
        throw 'Volume deletion is destructive. Set FAMILY_TREE_ALLOW_VOLUME_DELETE=YES before using -RemoveVolumes.'
    }
    $argsList += '--volumes'
}

& docker @argsList
if ($LASTEXITCODE -ne 0) { throw 'Failed to stop family-tree services.' }
Write-Host 'family-tree services have stopped.'
