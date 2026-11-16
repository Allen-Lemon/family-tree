[CmdletBinding()]
param(
    [string]$Destination = (Join-Path (Split-Path -Parent $PSScriptRoot) 'backups')
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root '.env'
$ComposeFile = Join-Path $Root 'deploy\compose.yaml'

& (Join-Path $PSScriptRoot 'bootstrap-check.ps1')
if (-not (Test-Path $Destination)) { New-Item -ItemType Directory -Path $Destination | Out-Null }
$Destination = (Resolve-Path $Destination).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$sqlFile = Join-Path $Destination "family-tree-$stamp.sql"
$uploadFile = Join-Path $Destination "family-tree-uploads-$stamp.tar.gz"
$containerDump = "/tmp/family-tree-$stamp.sql"

# The password is expanded only inside the container and never appears in this script or host process arguments.
& docker compose --env-file $EnvFile -f $ComposeFile exec -T mysql sh -c "exec mysqldump --single-transaction --routines --triggers --events -u root -p`"`$MYSQL_ROOT_PASSWORD`" `"`$MYSQL_DATABASE`" > '$containerDump'"
if ($LASTEXITCODE -ne 0) { throw 'Database dump failed.' }

$mysqlContainer = (& docker compose --env-file $EnvFile -f $ComposeFile ps -q mysql).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($mysqlContainer)) { throw 'Could not locate the MySQL container.' }
try {
    & docker cp "${mysqlContainer}:$containerDump" $sqlFile
    if ($LASTEXITCODE -ne 0) { throw 'Copying the database dump failed.' }
}
finally {
    & docker exec $mysqlContainer rm -f $containerDump | Out-Null
}

$projectName = 'family-tree'
$match = Select-String -Path $EnvFile -Pattern '^COMPOSE_PROJECT_NAME=(.+)$' | Select-Object -First 1
if ($match) { $projectName = $match.Matches[0].Groups[1].Value.Trim() }
$volumeName = "${projectName}_uploads"
& docker run --rm --mount "type=volume,src=$volumeName,dst=/data,readonly" --mount "type=bind,src=$Destination,dst=/backup" alpine:3.21 tar -czf "/backup/$(Split-Path -Leaf $uploadFile)" -C /data .
if ($LASTEXITCODE -ne 0) { throw 'Uploads backup failed; the database dump was retained.' }

$manifest = @{
    createdAt = (Get-Date).ToUniversalTime().ToString('o')
    database = (Split-Path -Leaf $sqlFile)
    uploads = (Split-Path -Leaf $uploadFile)
    project = $projectName
} | ConvertTo-Json
[IO.File]::WriteAllText((Join-Path $Destination "family-tree-$stamp.json"), $manifest, (New-Object Text.UTF8Encoding($false)))
Write-Host "Backup completed: $Destination"
