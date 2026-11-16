[CmdletBinding()]
param([string]$Destination = (Join-Path (Split-Path -Parent $PSScriptRoot) 'backups'))
$ErrorActionPreference = 'Stop'
# The root deployment has no local MySQL container. Never pretend an uploads
# archive is a complete database backup or silently omit the remote database.
throw 'This deployment uses external MySQL. Use remote mysqldump or managed database backup, copy the host uploads directory, and securely preserve the existing encryption key. See docs/deployment.md. No files were created.'
