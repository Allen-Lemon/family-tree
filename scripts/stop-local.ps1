param([ValidateSet('All','Backend','Frontend')][string]$Service='All')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$names=if($Service -eq 'All'){@('backend','frontend')}else{@($Service.ToLowerInvariant())}
foreach($name in $names){
  $pidFile=Join-Path $root "logs\$name.pid"
  if(!(Test-Path $pidFile)){continue}
  $processId=[int](Get-Content -LiteralPath $pidFile -Encoding UTF8 -Raw).Trim()
  $process=Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
  if(!$process){"$name is already stopped.";continue}
  $expected=if($name -eq 'backend'){Join-Path $root 'backend\target\family-tree-backend-0.0.1-SNAPSHOT.jar'}else{Join-Path $root 'frontend\node_modules\vite\bin\vite.js'}
  if(!$process.CommandLine -or !$process.CommandLine.Contains($expected)){throw "PID $processId no longer belongs to this project; refusing to stop it."}
  Stop-Process -Id $processId -Confirm:$false
  "$name stopped: PID $processId"
}
