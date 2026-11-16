param([ValidateSet('All','Backend','Frontend')][string]$Service='All')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$config=Join-Path $root '.env.local'
if(!(Test-Path $config)){throw 'Missing .env.local; configure local credentials first.'}
foreach($line in Get-Content -LiteralPath $config -Encoding UTF8){
  if($line -match '^\s*#' -or [string]::IsNullOrWhiteSpace($line)){continue}
  $pair=$line -split '=',2
  if($pair.Length -ne 2){throw 'Invalid .env.local line'}
  [Environment]::SetEnvironmentVariable($pair[0].Trim(),$pair[1],'Process')
}
$logs=Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
function Assert-FreePort([int]$port){
  if(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){throw "Port $port is occupied. Stop the existing service first."}
}
if($Service -in 'All','Backend'){
  $java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin\java.exe'}else{Join-Path $env:USERPROFILE 'family-tree-env\jdk-21.0.1\bin\java.exe'}
  if(!(Test-Path $java)){$java=(Get-Command java -ErrorAction Stop).Source}
  $jar=Join-Path $root 'backend\target\family-tree-backend-0.0.1-SNAPSHOT.jar'
  if(!(Test-Path $jar)){throw 'Build backend with Maven first.'}
  Assert-FreePort ([int]$env:SERVER_PORT)
  $process=Start-Process -FilePath $java -ArgumentList @('-Dfile.encoding=UTF-8','-jar',('"'+$jar+'"')) -WorkingDirectory $root -RedirectStandardOutput (Join-Path $logs 'backend.log') -RedirectStandardError (Join-Path $logs 'backend.err.log') -PassThru
  $process.Id | Set-Content -Encoding utf8 (Join-Path $logs 'backend.pid')
  "Backend started: PID $($process.Id); http://127.0.0.1:$($env:SERVER_PORT)"
}
if($Service -in 'All','Frontend'){
  Assert-FreePort 5173
  $node=(Get-Command node -ErrorAction Stop).Source
  $vite=Join-Path $root 'frontend\node_modules\vite\bin\vite.js'
  if(!(Test-Path $vite)){throw 'Install frontend dependencies with npm install first.'}
  $process=Start-Process -FilePath $node -ArgumentList @(('"'+$vite+'"'),'--host','127.0.0.1','--port','5173','--strictPort') -WorkingDirectory (Join-Path $root 'frontend') -RedirectStandardOutput (Join-Path $logs 'frontend.log') -RedirectStandardError (Join-Path $logs 'frontend.err.log') -PassThru
  $process.Id | Set-Content -Encoding utf8 (Join-Path $logs 'frontend.pid')
  "Frontend started: PID $($process.Id); http://127.0.0.1:5173"
}
