[CmdletBinding()]
param(
    [switch]$RemoveVolumes
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root '.env'
$ComposeFile = Join-Path $Root 'compose.yaml'

$argsList = @('compose', '--env-file', $EnvFile, '-f', $ComposeFile, 'down', '--remove-orphans')
if ($RemoveVolumes) {
    if ($env:FAMILY_TREE_ALLOW_VOLUME_DELETE -ne 'YES') {
        throw 'Deleting container-managed volumes requires FAMILY_TREE_ALLOW_VOLUME_DELETE=YES. The external database and bind-mounted uploads are never removed by this script.'
    }
    $argsList += '--volumes'
}

& docker @argsList
if ($LASTEXITCODE -ne 0) { throw 'Failed to stop family-tree services.' }
Write-Host 'family-tree services have stopped.'
