param([ValidateSet('frontend','admin','backend','mailpit','mysql')][string[]]$Services=@('frontend','admin','backend','mailpit','mysql'))
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path -LiteralPath (Split-Path $PSScriptRoot -Parent)).Path
$runtime=Join-Path $projectRoot '.runtime'
foreach($name in $Services){
  $record=Join-Path $runtime "$name-process.json"
  if(-not(Test-Path -LiteralPath $record)){continue}
  $saved=Get-Content -LiteralPath $record -Raw | ConvertFrom-Json
  $process=Get-Process -Id $saved.id -ErrorAction SilentlyContinue
  if($process -and [Math]::Abs(($process.StartTime.ToUniversalTime()-[datetime]$saved.started).TotalSeconds) -lt 2){
    # Stop only processes created by start-local.ps1, with matching PID + start time.
    $children=Get-CimInstance Win32_Process -Filter "ParentProcessId=$($process.Id)" -ErrorAction SilentlyContinue
    foreach($child in $children){Stop-Process -Id $child.ProcessId -ErrorAction SilentlyContinue}
    Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
  }
  Remove-Item -LiteralPath $record
}
Write-Output 'Stopped only recorded CineVerse processes. Database and media preserved.'
