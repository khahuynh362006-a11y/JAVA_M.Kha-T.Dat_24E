param([string]$XamppRoot='D:/xampp')
$ErrorActionPreference='Stop'
$admin=Join-Path $XamppRoot 'mysql/bin/mysqladmin.exe'
function WaitSqlReady {
  for($attempt=0;$attempt -lt 40;$attempt++){
    & $admin --no-defaults -h 127.0.0.1 -P 3306 --connect-timeout=1 ping --silent 2>$null | Out-Null
    if($LASTEXITCODE -eq 0){return}
    Start-Sleep -Milliseconds 500
  }
  throw 'Cổng 3306 có mở nhưng MariaDB chưa sẵn sàng nhận SQL.'
}
$socket=[Net.Sockets.TcpClient]::new()
$available=$false
try{$socket.Connect('127.0.0.1',3306);$available=$true}catch{}finally{$socket.Dispose()}
if($available){WaitSqlReady;Write-Output 'XAMPP database port 3306 already available.';return}
$projectRoot=Split-Path $PSScriptRoot -Parent
New-Item -ItemType Directory -Path (Join-Path $projectRoot '.runtime/logs') -Force | Out-Null
$binary=Join-Path $XamppRoot 'mysql/bin/mysqld.exe';$ini=Join-Path $XamppRoot 'mysql/bin/my.ini'
if(-not(Test-Path -LiteralPath $binary) -or -not(Test-Path -LiteralPath $ini)){throw 'Không tìm thấy MySQL của XAMPP. Truyền -XamppRoot đúng thư mục cài đặt.'}
$process=Start-Process -FilePath $binary -ArgumentList @("--defaults-file=`"$ini`"",'--bind-address=127.0.0.1','--console') -WorkingDirectory (Join-Path $XamppRoot 'mysql/bin') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $projectRoot '.runtime/logs/xampp-mysql.log') -RedirectStandardError (Join-Path $projectRoot '.runtime/logs/xampp-mysql-error.log')
@{id=$process.Id;started=$process.StartTime.ToUniversalTime().ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $projectRoot '.runtime/xampp-process.json')
WaitSqlReady
Write-Output 'Started XAMPP MariaDB on 127.0.0.1:3306.'
return
