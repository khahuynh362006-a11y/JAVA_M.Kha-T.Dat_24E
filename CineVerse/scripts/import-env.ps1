$projectRoot=Split-Path $PSScriptRoot -Parent
$envPath=Join-Path $projectRoot '.env'
if(-not(Test-Path -LiteralPath $envPath)){& (Join-Path $PSScriptRoot 'setup-env.ps1')}
foreach($line in Get-Content -LiteralPath $envPath){
  if($line -match '^([A-Z][A-Z0-9_]*)=(.*)$'){[Environment]::SetEnvironmentVariable($matches[1],$matches[2].Trim(), 'Process')}
}
