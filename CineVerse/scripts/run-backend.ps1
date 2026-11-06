$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'import-env.ps1')
$projectRoot=Split-Path $PSScriptRoot -Parent
if(-not $env:JAVA_HOME){$env:JAVA_HOME=(Get-ChildItem 'C:/Program Files/Java' -Directory | Where-Object Name -Like 'jdk-21*' | Sort-Object Name -Descending | Select-Object -First 1).FullName}
Set-Location (Join-Path $projectRoot 'backend')
& "$env:JAVA_HOME/bin/java.exe" -jar target/cineverse-1.0.0.jar
exit $LASTEXITCODE
