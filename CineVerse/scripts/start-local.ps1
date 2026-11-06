param([string]$MySqlHome='D:/laragon/bin/mysql/mysql-8.4.3-winx64',[string]$MailpitExe='D:/laragon/bin/mailpit/1.22.3/mailpit.exe',[switch]$SkipBuild)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path -LiteralPath (Split-Path $PSScriptRoot -Parent)).Path
$runtime=Join-Path $projectRoot '.runtime';$data=Join-Path $runtime 'mysql';$logs=Join-Path $runtime 'logs'
New-Item -ItemType Directory -Path $runtime,$logs -Force | Out-Null
& (Join-Path $PSScriptRoot 'setup-env.ps1')
. (Join-Path $PSScriptRoot 'import-env.ps1')
function IsListening([int]$Port){$socket=[Net.Sockets.TcpClient]::new();try{$socket.Connect('127.0.0.1',$Port);return $true}catch{return $false}finally{$socket.Dispose()}}
function WaitPort([int]$Port){for($i=0;$i -lt 90;$i++){if(IsListening $Port){return};Start-Sleep -Milliseconds 500};throw "Port $Port did not become ready; inspect .runtime/logs"}
function HiddenProcess($Name,$File,$Arguments,$WorkingDirectory){
  $process=Start-Process -FilePath $File -ArgumentList $Arguments -WorkingDirectory $WorkingDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logs "$Name.log") -RedirectStandardError (Join-Path $logs "$Name-error.log")
  @{id=$process.Id;path=$File;started=$process.StartTime.ToUniversalTime().ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime "$Name-process.json")
}
if($env:DB_PROVIDER -eq 'xampp'){
  $xamppRoot=if($env:XAMPP_ROOT){$env:XAMPP_ROOT}else{'D:/xampp'}
  & (Join-Path $PSScriptRoot 'start-xampp-mysql.ps1') -XamppRoot $xamppRoot
}elseif(-not(IsListening 3307)){
  if(-not(Test-Path -LiteralPath "$MySqlHome/bin/mysqld.exe")){throw 'MySQL not found. Pass -MySqlHome or use Docker Compose.'}
  $fresh=-not(Test-Path -LiteralPath (Join-Path $data 'mysql'))
  if($fresh){New-Item -ItemType Directory -Path $data -Force | Out-Null;& "$MySqlHome/bin/mysqld.exe" --no-defaults --initialize-insecure "--basedir=$MySqlHome" "--datadir=$data" --console;if($LASTEXITCODE -ne 0){throw 'MySQL initialization failed'}}
  HiddenProcess 'mysql' "$MySqlHome/bin/mysqld.exe" @('--no-defaults',"--basedir=`"$MySqlHome`"","--datadir=`"$data`"",'--port=3307','--bind-address=127.0.0.1','--mysqlx=OFF','--console') $projectRoot
  WaitPort 3307
  if($fresh){
    $sql="CREATE DATABASE cineverse CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER 'cineverse'@'127.0.0.1' IDENTIFIED BY '$env:DB_PASSWORD'; GRANT ALL PRIVILEGES ON cineverse.* TO 'cineverse'@'127.0.0.1'; ALTER USER 'root'@'localhost' IDENTIFIED BY '$env:MYSQL_ROOT_PASSWORD';"
    $sql | & "$MySqlHome/bin/mysql.exe" --no-defaults --default-character-set=utf8mb4 -h 127.0.0.1 -P 3307 -u root
    if($LASTEXITCODE -ne 0){throw 'Database user creation failed'}
  }
}
if(-not(IsListening 8025)){
  if(-not(Test-Path -LiteralPath $MailpitExe)){throw 'Mailpit not found. Pass -MailpitExe or use Docker Compose.'}
  HiddenProcess 'mailpit' $MailpitExe @('--listen','127.0.0.1:8025','--smtp','127.0.0.1:1025','--database',"`"$runtime/mailpit.db`"") $projectRoot
  WaitPort 8025
}
if(-not $SkipBuild){& (Join-Path $PSScriptRoot 'stop-local.ps1') -Services backend;& (Join-Path $PSScriptRoot 'maven.ps1') -B -q package;Push-Location (Join-Path $projectRoot 'frontend');try{if(-not(Test-Path node_modules)){npm.cmd ci --no-audit --no-fund};npm.cmd run build;if($LASTEXITCODE -ne 0){throw 'Frontend build failed'}}finally{Pop-Location};Push-Location (Join-Path $projectRoot 'admin');try{npm.cmd run build;if($LASTEXITCODE -ne 0){throw 'Admin build failed'}}finally{Pop-Location}}
if(-not(IsListening 8080)){HiddenProcess 'backend' (Join-Path $PSHOME 'pwsh.exe') @('-NoProfile','-File',"`"$PSScriptRoot/run-backend.ps1`"") (Join-Path $projectRoot 'backend');WaitPort 8080}
if(-not(IsListening 5173)){HiddenProcess 'frontend' (Get-Command node.exe).Source @('node_modules/vite/bin/vite.js','--host','127.0.0.1','--port','5173') (Join-Path $projectRoot 'frontend');WaitPort 5173}
if(-not(IsListening 5174)){HiddenProcess 'admin' (Get-Command node.exe).Source @("`"$projectRoot/frontend/node_modules/vite/bin/vite.js`"",'--config',"`"$projectRoot/frontend/vite.admin.config.ts`"") (Join-Path $projectRoot 'admin');WaitPort 5174}
Write-Output 'User: http://localhost:5173 | Admin: http://localhost:5174 | API: http://localhost:8080 | Mailpit: http://localhost:8025'
Write-Output 'Demo credentials: pwsh -File scripts/show-demo.ps1'
