$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'stop-local.ps1') -Services backend
& (Join-Path $PSScriptRoot 'start-local.ps1') -SkipBuild
for($attempt=0;$attempt -lt 30;$attempt++){
  try{
    $health=Invoke-RestMethod 'http://localhost:8080/actuator/health' -TimeoutSec 2
    if($health.status -eq 'UP'){Write-Output 'Backend healthy.';return}
  }catch{Start-Sleep -Seconds 1}
}
throw 'Backend chưa healthy. Xem .runtime/logs/backend.log.'
